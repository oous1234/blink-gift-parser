import logging
import asyncio
import traceback
from telethon import functions, types, errors
from tg_client import tg_session
from schemas import (
    InventoryItem, InventoryResponse, GiftMetadataResponse,
    GiftAttribute, SearchEntity, SearchResponse, EntityType
)
from fastapi import HTTPException
from typing import List, Set, Optional

logger = logging.getLogger(__name__)

class TelegramService:
    @staticmethod
    async def get_nft_count(peer) -> int:
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
            id=entity.id,
            username=getattr(entity, 'username', None),
            title=title,
            type=entity_type,
            nft_count=0,
            verified=getattr(entity, 'verified', False)
        )

    @staticmethod
    async def resolve_peer(user_id: str):
        try:
            if user_id.lower() == "me":
                return await tg_session.client.get_me()

            if user_id.isdigit() or (user_id.startswith('-') and user_id[1:].isdigit()):
                try:
                    return await tg_session.client.get_entity(int(user_id))
                except Exception:
                    full = await tg_session.client(functions.users.GetFullUserRequest(id=int(user_id)))
                    return full.users[0]

            return await tg_session.client.get_entity(user_id)
        except Exception as e:
            raise HTTPException(status_code=404, detail=f"User not found: {str(e)}")

    @staticmethod
    async def get_gift_metadata(slug: str) -> GiftMetadataResponse:
        try:
            # Запрос к Telegram
            result = await tg_session.client(functions.payments.GetUniqueStarGiftRequest(slug=slug))
            gift = result.gift

            # Парсинг атрибутов через строковые имена классов, чтобы избежать AttributeError
            attributes = []
            for attr in getattr(gift, 'attributes', []):
                class_name = attr.__class__.__name__
                rarity = getattr(attr, 'rarity_permille', 0) / 10

                if class_name == 'StarGiftAttributeModel':
                    attributes.append(GiftAttribute(type="model", name=attr.name, rarity_percent=rarity))

                elif class_name == 'StarGiftAttributeBackdrop':
                    attributes.append(GiftAttribute(
                        type="backdrop",
                        name=attr.name,
                        rarity_percent=rarity,
                        colors={
                            "center": hex(getattr(attr, 'center_color', 0)),
                            "edge": hex(getattr(attr, 'edge_color', 0)),
                            "text": hex(getattr(attr, 'text_color', 0))
                        }
                    ))

                elif class_name == 'StarGiftAttributeSymbol':
                    attributes.append(GiftAttribute(type="symbol", name=attr.name, rarity_percent=rarity))

            # Безопасное определение владельца
            owner_id = None
            if hasattr(gift, 'owner_id') and gift.owner_id:
                if hasattr(gift.owner_id, 'user_id'):
                    owner_id = gift.owner_id.user_id
                elif hasattr(gift.owner_id, 'channel_id'):
                    owner_id = gift.owner_id.channel_id

            return GiftMetadataResponse(
                id=str(gift.id),
                title=gift.title,
                slug=gift.slug,
                serial_number=gift.num,
                total_issued=getattr(gift, 'availability_total', 0),
                owner_id=owner_id,
                owner_name=getattr(gift, 'owner_name', None),
                owner_address=getattr(gift, 'owner_address', None),
                attributes=attributes,
                is_resalable=getattr(gift, 'resell_amount', None) is not None,
                price_amount=getattr(gift, 'value_amount', None),
                price_currency=getattr(gift, 'value_currency', "STR")
            )

        except errors.RPCError as e:
            logger.error(f"Telegram RPC Error: {e.message}")
            raise HTTPException(status_code=404, detail=f"Telegram Error: {e.message}")
        except Exception as e:
            logger.error(f"Metadata error: {traceback.format_exc()}")
            raise HTTPException(status_code=500, detail=f"Internal Server Error: {str(e)}")

    @staticmethod
    async def get_user_inventory(user_id: str, offset: str = "", limit: int = 100) -> InventoryResponse:
        try:
            entity = await TelegramService.resolve_peer(user_id)
            result = await tg_session.client(functions.payments.GetSavedStarGiftsRequest(
                peer=entity, offset=offset or "0", limit=limit, exclude_unlimited=True
            ))

            gifts = []
            for i in getattr(result, 'gifts', []):
                # Проверяем на StarGiftUnique через имя класса для надежности
                if i.gift.__class__.__name__ == 'StarGiftUnique':
                    gifts.append(InventoryItem(
                        gift_id=str(i.gift.id),
                        slug=i.gift.slug,
                        date=i.date,
                        nft_address=getattr(i.gift, 'gift_address', None),
                        serial_number=i.gift.num
                    ))

            return InventoryResponse(
                user_id=str(user_id),
                total_count=getattr(result, 'count', len(gifts)),
                items=gifts,
                next_offset=getattr(result, 'next_offset', None)
            )
        except Exception as e:
            logger.error(f"Inventory error: {e}")
            raise HTTPException(status_code=500, detail=str(e))

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
            logger.error(f"Search error: {e}")
            raise HTTPException(status_code=500, detail=str(e))

    @staticmethod
    async def deep_scan_whales(limit: int = 100) -> SearchResponse:
        prefixes = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z']
        found_entities = []
        seen_ids = set()

        try:
            for p in prefixes:
                res = await tg_session.client(functions.contacts.SearchRequest(q=p, limit=100))
                for e in (res.users + res.chats):
                    if e.id not in seen_ids and not getattr(e, 'bot', False):
                        found_entities.append(e)
                        seen_ids.add(e.id)
                await asyncio.sleep(0.1)

            results: List[SearchEntity] = []
            batch_size = 15
            for i in range(0, len(found_entities), batch_size):
                batch = found_entities[i:i+batch_size]
                counts = await asyncio.gather(*[TelegramService.get_nft_count(e) for e in batch])
                for j, e in enumerate(batch):
                    if counts[j] > 0:
                        item = TelegramService._parse_telegram_entity(e)
                        item.nft_count = counts[j]
                        results.append(item)
                await asyncio.sleep(0.2)

            results.sort(key=lambda x: x.nft_count, reverse=True)
            return SearchResponse(query="DEEP_GLOBAL_SCAN", results=results[:limit])
        except Exception as e:
            logger.error(f"Deep scan error: {e}")
            raise HTTPException(status_code=500, detail="Deep scan failed")