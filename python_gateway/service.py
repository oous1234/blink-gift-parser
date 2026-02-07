from telethon import functions, types
from tg_client import tg_session
from schemas import InventoryItem, InventoryResponse, GiftMetadataResponse, GiftAttribute

class TelegramService:
    @staticmethod
    async def get_user_inventory(user_id: str, offset: str = "", limit: int = 100) -> InventoryResponse:
        """
        Получение списка уникальных подарков пользователя через MTProto.
        """
        try:
            # Определяем цель: ID, username или 'me'
            if user_id.lower() == "me":
                target = "me"
            elif user_id.isdigit():
                target = int(user_id)
            else:
                target = user_id

            entity = await tg_session.client.get_entity(target)

            # Запрос к Telegram
            result = await tg_session.client(functions.payments.GetSavedStarGiftsRequest(
                peer=entity,
                offset=offset if offset else "",
                limit=limit,
                exclude_unlimited=True
            ))

            items = []
            for item in result.gifts:
                gift = item.gift
                if not isinstance(gift, types.StarGiftUnique):
                    continue

                items.append(InventoryItem(
                    gift_id=str(gift.id),
                    slug=gift.slug,
                    date=item.date,
                    nft_address=getattr(gift, 'gift_address', None),
                    serial_number=gift.num
                ))

            return InventoryResponse(
                user_id=str(user_id),
                total_count=getattr(result, 'count', len(items)),
                items=items,
                next_offset=result.next_offset if hasattr(result, 'next_offset') else None
            )
        except Exception as e:
            print(f"Error in get_user_inventory: {e}")
            raise e

    @staticmethod
    async def get_gift_metadata(slug: str) -> GiftMetadataResponse:
        """
        Получение детальной информации о шаблоне подарка.
        """
        try:
            result = await tg_session.client(functions.payments.GetUniqueStarGiftRequest(slug=slug))
            gift = result.gift

            attributes = []
            for attr in getattr(gift, 'attributes', []):
                if isinstance(attr, types.StarGiftAttributeModel):
                    attributes.append(GiftAttribute(
                        type="model",
                        name=attr.name,
                        rarity_percent=attr.rarity_permille / 10
                    ))
                elif isinstance(attr, types.StarGiftAttributeBackdrop):
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
                elif isinstance(attr, types.StarGiftAttributeSymbol):
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
                is_resalable=getattr(gift, 'can_export', False) or hasattr(gift, 'resell_amount'),
                price_amount=getattr(gift, 'value_amount', None),
                price_currency=getattr(gift, 'value_currency', "STR")
            )
        except Exception as e:
            print(f"Error in get_gift_metadata: {e}")
            raise e