from telethon import functions, types
from tg_client import tg_session
from schemas import InventoryItem, InventoryResponse, GiftMetadataResponse, GiftAttribute

class TelegramService:
    @staticmethod
    async def get_user_inventory(user_id: str, offset: str = "", limit: int = 100) -> InventoryResponse:
        try:
            if user_id.lower() == "me":
                target = "me"
            elif user_id.isdigit():
                target = int(user_id)
            else:
                target = user_id

            entity = await tg_session.client.get_entity(target)
            result = await tg_session.client(functions.payments.GetSavedStarGiftsRequest(
                peer=entity,
                offset=offset if offset else "",
                limit=limit,
                exclude_unlimited=True
            ))

            items = []
            for item in result.gifts:
                gift = item.gift
                # Проверка через имя класса, если типы еще не обновились в библиотеке
                if gift.__class__.__name__ != 'StarGiftUnique':
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
                next_offset=getattr(result, 'next_offset', None)
            )
        except Exception as e:
            print(f"Error in get_user_inventory: {e}")
            raise e

    @staticmethod
    async def get_gift_metadata(slug: str) -> GiftMetadataResponse:
        try:
            result = await tg_session.client(functions.payments.GetUniqueStarGiftRequest(slug=slug))
            gift = result.gift
            attributes = []

            for attr in getattr(gift, 'attributes', []):
                attr_type = attr.__class__.__name__

                if attr_type == 'StarGiftAttributeModel':
                    attributes.append(GiftAttribute(
                        type="model",
                        name=attr.name,
                        rarity_percent=attr.rarity_permille / 10
                    ))
                elif attr_type == 'StarGiftAttributeBackdrop':
                    attributes.append(GiftAttribute(
                        type="backdrop",
                        name=attr.name,
                        rarity_percent=attr.rarity_permille / 10,
                        colors={
                            "center": hex(getattr(attr, 'center_color', 0)),
                            "edge": hex(getattr(attr, 'edge_color', 0)),
                            "text": hex(getattr(attr, 'text_color', 0))
                        }
                    ))
                elif attr_type == 'StarGiftAttributeSymbol':
                    attributes.append(GiftAttribute(
                        type="symbol",
                        name=attr.name,
                        rarity_percent=attr.rarity_permille / 10
                    ))

            return GiftMetadataResponse(
                id=str(gift.id),
                title=gift.title,
                slug=gift.slug,
                serial_number=gift.num,
                total_issued=gift.availability_total,
                owner_id=getattr(gift.owner_id, 'user_id', None) if gift.owner_id else None,
                owner_name=getattr(gift, 'owner_name', None),
                owner_address=getattr(gift, 'owner_address', None),
                attributes=attributes,
                is_resalable=getattr(gift, 'can_export', False) or hasattr(gift, 'resell_amount'),
                price_amount=getattr(gift, 'value_amount', None),
                price_currency=getattr(gift, 'value_currency', "STR")
            )
        except Exception as e:
            print(f"Error in get_gift_metadata: {e}")
            raise e