from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import datetime

class InventoryItem(BaseModel):
    gift_id: str
    slug: str
    date: datetime
    nft_address: Optional[str] = None
    serial_number: int

class InventoryResponse(BaseModel):
    user_id: str
    total_count: int
    items: List[InventoryItem]
    next_offset: Optional[str]

class GiftAttribute(BaseModel):
    type: str
    name: str
    rarity_percent: float
    colors: Optional[Dict[str, str]] = None

class GiftMetadataResponse(BaseModel):
    id: str
    title: str
    slug: str
    serial_number: int
    total_issued: int
    owner_id: Optional[int] # Можно заменить на str для безопасности в JS
    owner_name: Optional[str]
    owner_address: Optional[str]
    attributes: List[GiftAttribute]
    is_resalable: bool
    price_amount: Optional[int]
    price_currency: str