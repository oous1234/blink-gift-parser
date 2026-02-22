import logging
import asyncio
from telethon import functions, types, errors
from tg_client import tg_session
from schemas import (
    InventoryItem, InventoryResponse, GiftMetadataResponse,
    GiftAttribute, SearchEntity, SearchResponse, EntityType
)
from fastapi import HTTPException
from typing import List, Set

logger = logging.getLogger(__name__)

class TelegramService:
    @staticmethod
    async def get_nft_count(peer) -> int:
        """Получает количество NFT-подарков"""
        try:
            result = await tg_session.client(functions.payments.GetSavedStarGiftsRequest(
                peer=peer, offset="", limit=1, exclude_unlimited=True
            ))
            return getattr(result, 'count', 0)
        except Exception:
            return 0

    @staticmethod
    def _parse_telegram_entity(entity) -> SearchEntity:
        entity_type = EntityType.UNKNOWN
        title = "Unknown"
        if isinstance(entity, types.User):
            entity_type = EntityType.BOT if entity.bot else EntityType.USER
            title = f"{entity.first_name or ''} {entity.last_name or ''}".strip() or "Hidden"
        elif isinstance(entity, types.Channel):
            entity_type = EntityType.CHANNEL if entity.broadcast else EntityType.GROUP
            title = entity.title
        return SearchEntity(
            id=entity.id, username=getattr(entity, 'username', None),
            title=title, type=entity_type, nft_count=0,
            verified=getattr(entity, 'verified', False)
        )

    @staticmethod
    async def deep_scan_whales(limit: int = 100) -> SearchResponse:
        """
        Метод МАКСИМАЛЬНОГО сканирования.
        Перебирает алфавит и популярные префиксы для охвата всего Telegram.
        """
        # Популярные префиксы, покрывающие ~80% имен/ников
        prefixes = [
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'th', 'er', 'on', 'an', 're', 'he', 'in', 'ed', 'st'
        ]

        found_entities = []
        seen_ids: Set[int] = set()

        logger.info(f"Starting Deep Scan across {len(prefixes)} search iterations...")

        try:
            # 1. Сбор базы кандидатов (итеративный поиск)
            for p in prefixes:
                try:
                    res = await tg_session.client(functions.contacts.SearchRequest(q=p, limit=100))
                    for e in (res.users + res.chats):
                        if e.id not in seen_ids and not getattr(e, 'bot', False):
                            found_entities.append(e)
                            seen_ids.add(e.id)
                    # Небольшая пауза, чтобы Telegram не заблокировал за спам запросами
                    await asyncio.sleep(0.2)
                except errors.FloodWaitError as f:
                    await asyncio.sleep(f.seconds)

            logger.info(f"Total unique candidates found: {len(found_entities)}. Checking NFT counts...")

            # 2. Проверка NFT счетчиков (пачками по 10, чтобы не упасть)
            results: List[SearchEntity] = []
            batch_size = 15

            for i in range(0, len(found_entities), batch_size):
                batch = found_entities[i:i+batch_size]
                counts = await asyncio.gather(*[TelegramService.get_nft_count(e) for e in batch])

                for j, e in enumerate(batch):
                    count = counts[j]
                    if count > 0:
                        item = TelegramService._parse_telegram_entity(e)
                        item.nft_count = count
                        results.append(item)

                # Логируем прогресс
                if i % 150 == 0:
                    logger.info(f"Processed {i}/{len(found_entities)} candidates...")

                await asyncio.sleep(0.5) # Пауза между пачками

            # 3. Сортировка всего списка по убыванию NFT
            results.sort(key=lambda x: x.nft_count, reverse=True)

            return SearchResponse(query="DEEP_GLOBAL_SCAN", results=results[:limit])

        except Exception as e:
            logger.error(f"Deep scan error: {e}")
            raise HTTPException(status_code=500, detail="Deep scan failed")

    @staticmethod
    async def resolve_peer(user_id: str):
        try:
            if user_id.lower() == "me": return await tg_session.client.get_me()
            if user_id.isdigit() or (user_id.startswith('-') and user_id[1:].isdigit()):
                try:
                    return await tg_session.client.get_entity(int(user_id))
                except:
                    full = await tg_session.client(functions.users.GetFullUserRequest(id=int(user_id)))
                    return full.users[0]
            return await tg_session.client.get_entity(user_id)
        except Exception as e:
            raise HTTPException(status_code=404, detail=str(e))

    @staticmethod
    async def search_entities(query: str, limit: int = 20) -> SearchResponse:
        try:
            res = await tg_session.client(functions.contacts.SearchRequest(q=query, limit=limit))
            unique = []
            seen = set()
            for e in (res.users + res.chats):
                if e.id not in seen and not getattr(e, 'bot', False):
                    unique.append(e)
                    seen.add(e.id)

            counts = await asyncio.gather(*[TelegramService.get_nft_count(e) for e in unique])
            results = []
            for i, e in enumerate(unique):
                item = TelegramService._parse_telegram_entity(e)
                item.nft_count = counts[i]
                results.append(item)
            results.sort(key=lambda x: x.nft_count, reverse=True)
            return SearchResponse(query=query, results=results)
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    @staticmethod
    async def get_user_inventory(user_id: str, offset: str = "", limit: int = 100) -> InventoryResponse:
        try:
            entity = await TelegramService.resolve_peer(user_id)
            result = await tg_session.client(functions.payments.GetSavedStarGiftsRequest(
                peer=entity, offset=offset or "0", limit=limit, exclude_unlimited=True
            ))
            gifts = [
                InventoryItem(
                    gift_id=str(i.gift.id), slug=i.gift.slug, date=i.date,
                    nft_address=getattr(i.gift, 'gift_address', None), serial_number=i.gift.num
                ) for i in getattr(result, 'gifts', []) if isinstance(i.gift, types.StarGiftUnique)
            ]
            return InventoryResponse(
                user_id=str(user_id), total_count=getattr(result, 'count', len(gifts)),
                items=gifts, next_offset=getattr(result, 'next_offset', None)
            )
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    @staticmethod
    async def get_gift_metadata(slug: str) -> GiftMetadataResponse:
        try:
            result = await tg_session.client(functions.payments.GetUniqueStarGiftRequest(slug=slug))
            gift = result.gift
            attributes = []
            for attr in getattr(gift, 'attributes', []):
                attr_name = type(attr).__name__
                rarity = attr.rarity_permille / 10
                if attr_name == 'StarGiftAttributeModel':
                    attributes.append(GiftAttribute(type="model", name=attr.name, rarity_percent=rarity))
                elif attr_name == 'StarGiftAttributeBackdrop':
                    attributes.append(GiftAttribute(
                        type="backdrop", name=attr.name, rarity_percent=rarity,
                        colors={"center": hex(attr.center_color), "edge": hex(attr.edge_color), "text": hex(attr.text_color)}
                    ))
                elif attr_name == 'StarGiftAttributeSymbol':
                    attributes.append(GiftAttribute(type="symbol", name=attr.name, rarity_percent=rarity))

            owner_id = gift.owner_id.user_id if hasattr(gift.owner_id, 'user_id') else None
            return GiftMetadataResponse(
                id=str(gift.id), title=gift.title, slug=gift.slug, serial_number=gift.num,
                total_issued=gift.availability_total, owner_id=owner_id, owner_name=gift.owner_name,
                owner_address=gift.owner_address, attributes=attributes,
                is_resalable=hasattr(gift, 'resell_amount') and gift.resell_amount is not None,
                price_amount=getattr(gift, 'value_amount', None), price_currency=getattr(gift, 'value_currency', "STR")
            )
        except Exception as e:
            raise HTTPException(status_code=404, detail="Gift not found")