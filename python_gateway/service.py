import logging
import asyncio
import traceback
from telethon import functions, types, errors
from session_manager import farm
from schemas import (
    InventoryItem, InventoryResponse, GiftMetadataResponse,
    GiftAttribute, SearchEntity, SearchResponse, EntityType
)
from fastapi import HTTPException
from typing import List, Optional

logger = logging.getLogger(__name__)

class TelegramService:

    @staticmethod
    async def get_user_inventory(user_id: str, offset: str = "", limit: int = 100) -> InventoryResponse:
        """Получает инвентарь пользователя, используя свободную сессию из пула."""

        async def _logic(client):
            try:
                if user_id.isdigit() or (user_id.startswith('-') and user_id[1:].isdigit()):
                    entity = await client.get_entity(int(user_id))
                else:
                    entity = await client.get_entity(user_id)
            except Exception as e:
                logger.error(f"Entity resolution failed for {user_id}: {e}")
                raise HTTPException(status_code=404, detail="Target user not found")

            result = await client(functions.payments.GetSavedStarGiftsRequest(
                peer=entity,
                offset=offset or "0",
                limit=limit,
                exclude_unlimited=True
            ))

            gifts = []
            for i in getattr(result, 'gifts', []):
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

        try:
            return await farm.exec_with_retry(_logic)
        except Exception as e:
            logger.error(f"Inventory fetch error: {traceback.format_exc()}")
            raise HTTPException(status_code=500, detail=str(e))

    @staticmethod
    async def get_gift_metadata(slug: str) -> GiftMetadataResponse:
        """Получает детальные метаданные конкретного подарка (модель, фон, символ)."""

        async def _logic(client):
            result = await client(functions.payments.GetUniqueStarGiftRequest(slug=slug))
            gift = result.gift

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
                            "pattern": hex(getattr(attr, 'pattern_color', 0))
                        }
                    ))
                elif class_name == 'StarGiftAttributeSymbol':
                    attributes.append(GiftAttribute(type="symbol", name=attr.name, rarity_percent=rarity))

            owner_id = None
            if hasattr(gift, 'owner_id') and gift.owner_id:
                if isinstance(gift.owner_id, types.PeerUser):
                    owner_id = gift.owner_id.user_id
                elif isinstance(gift.owner_id, types.PeerChannel):
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

        try:
            return await farm.exec_with_retry(_logic)
        except Exception as e:
            logger.error(f"Metadata error for {slug}: {e}")
            raise HTTPException(status_code=404, detail=f"Metadata not found: {str(e)}")

    @staticmethod
    async def search_entities(query: str, limit: int = 20) -> SearchResponse:
        """Глобальный поиск пользователей/каналов с подсчетом их подарков."""

        async def _logic(client):
            res = await client(functions.contacts.SearchRequest(q=query, limit=limit))
            unique_entities = []
            seen_ids = set()

            for e in (res.users + res.chats):
                if e.id not in seen_ids and not getattr(e, 'bot', False):
                    unique_entities.append(e)
                    seen_ids.add(e.id)

            results = []
            for e in unique_entities:
                nft_count = 0
                try:
                    nft_res = await client(functions.payments.GetSavedStarGiftsRequest(
                        peer=e, offset="", limit=1, exclude_unlimited=True
                    ))
                    nft_count = getattr(nft_res, 'count', 0)
                except:
                    pass

                e_type = EntityType.USER
                title = "Unknown"
                if isinstance(e, types.User):
                    e_type = EntityType.BOT if e.bot else EntityType.USER
                    title = f"{e.first_name or ''} {e.last_name or ''}".strip() or "Hidden"
                elif isinstance(e, types.Channel):
                    e_type = EntityType.CHANNEL if e.broadcast else EntityType.GROUP
                    title = e.title

                results.append(SearchEntity(
                    id=e.id,
                    username=getattr(e, 'username', None),
                    title=title,
                    type=e_type,
                    nft_count=nft_count,
                    verified=getattr(e, 'verified', False)
                ))

            results.sort(key=lambda x: x.nft_count, reverse=True)
            return SearchResponse(query=query, results=results)

        try:
            return await farm.exec_with_retry(_logic)
        except Exception as e:
            logger.error(f"Search error: {e}")
            raise HTTPException(status_code=500, detail="Search failed")