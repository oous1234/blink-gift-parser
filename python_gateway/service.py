import logging
from telethon import functions, types, errors
from tg_client import tg_session
from schemas import InventoryItem, InventoryResponse, GiftMetadataResponse, GiftAttribute
from fastapi import HTTPException

logger = logging.getLogger(__name__)

class TelegramService:
    @staticmethod
    async def resolve_peer(user_id: str):
        """
        Логика резолва сущности, адаптированная из твоего tele_manager.py
        """
        try:
            # 1. Обработка 'me'
            if user_id.lower() == "me":
                return await tg_session.client.get_me()

            # 2. Если передан числовой ID
            if user_id.isdigit() or (user_id.startswith('-') and user_id[1:].isdigit()):
                search_id = int(user_id)
                try:
                    # Пытаемся достать из кэша сессии
                    return await tg_session.client.get_entity(search_id)
                except (ValueError, errors.rpcerrorlist.PeerIdInvalidError):
                    # Если в кэше нет, пробуем "прогрузить" через GetFullUser
                    # Для сессии пользователя (не бота) это часто срабатывает
                    logger.info(f"ID {search_id} not in cache, forcing full user lookup...")
                    full_info = await tg_session.client(functions.users.GetFullUserRequest(id=search_id))
                    return full_info.users[0]

            # 3. Если передан username или ссылка
            return await tg_session.client.get_entity(user_id)

        except Exception as e:
            logger.error(f"Entity search error for {user_id}: {e}")
            # Если совсем не нашли - кидаем 404 как в твоем tele_manager
            raise HTTPException(status_code=404, detail=f"User entity not found: {str(e)}")

    @staticmethod
    async def get_user_inventory(user_id: str, offset: str = "", limit: int = 100) -> InventoryResponse:
        """
        Получение инвентаря с использованием улучшенного резолва
        """
        try:
            entity = await TelegramService.resolve_peer(user_id)

            result = await tg_session.client(functions.payments.GetSavedStarGiftsRequest(
                peer=entity,
                offset=offset if offset else "0", # В твоем коде был str(offset)
                limit=limit,
                exclude_unlimited=True
            ))

            gifts = []
            if not result or not hasattr(result, 'gifts'):
                return InventoryResponse(user_id=user_id, total_count=0, items=[], next_offset=None)

            for item in result.gifts:
                gift_raw = item.gift
                if not isinstance(gift_raw, types.StarGiftUnique):
                    continue

                nft_address = getattr(gift_raw, 'gift_address', None)

                gifts.append(InventoryItem(
                    gift_id=gift_raw.id,
                    slug=gift_raw.slug,
                    date=item.date,
                    nft_address=nft_address,
                    serial_number=gift_raw.num
                ))

            return InventoryResponse(
                user_id=str(user_id),
                total_count=getattr(result, 'count', len(gifts)),
                items=gifts,
                next_offset=result.next_offset if hasattr(result, 'next_offset') else None
            )
        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"Inventory error: {e}")
            raise HTTPException(status_code=500, detail=str(e))

    @staticmethod
    async def get_gift_metadata(slug: str) -> GiftMetadataResponse:
        """
        Получение метаданных (логика из твоего get_gift_by_slug)
        """
        try:
            result = await tg_session.client(functions.payments.GetUniqueStarGiftRequest(slug=slug))
            gift = result.gift

            attributes = []
            for attr in getattr(gift, 'attributes', []):
                attr_name = type(attr).__name__

                if attr_name == 'StarGiftAttributeModel':
                    attributes.append(GiftAttribute(
                        type="model",
                        name=attr.name,
                        rarity_percent=attr.rarity_permille / 10
                    ))
                elif attr_name == 'StarGiftAttributeBackdrop':
                    attributes.append(GiftAttribute(
                        type="backdrop",
                        name=attr.name,
                        rarity_percent=attr.rarity_permille / 10,
                        colors={
                            "center": hex(attr.center_color),
                            "edge": hex(attr.edge_color),
                            "text": hex(attr.text_color)
                        }
                    ))
                elif attr_name == 'StarGiftAttributeSymbol':
                    attributes.append(GiftAttribute(
                        type="symbol",
                        name=attr.name,
                        rarity_percent=attr.rarity_permille / 10
                    ))

            return GiftMetadataResponse(
                id=gift.id,
                title=gift.title,
                slug=gift.slug,
                serial_number=gift.num,
                total_issued=gift.availability_total,
                owner_id=getattr(gift.owner_id, 'user_id', None) if gift.owner_id else None,
                owner_name=gift.owner_name,
                owner_address=gift.owner_address,
                attributes=attributes,
                is_resalable=hasattr(gift, 'resell_amount') and gift.resell_amount is not None,
                price_amount=getattr(gift, 'value_amount', None),
                price_currency=getattr(gift, 'value_currency', "STR")
            )
        except Exception as e:
            logger.error(f"Metadata error for {slug}: {e}")
            raise HTTPException(status_code=404, detail="Gift not found")