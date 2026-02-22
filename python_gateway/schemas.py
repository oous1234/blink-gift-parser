from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import datetime
from enum import Enum

class EntityType(str, Enum):
    USER = "user"
    BOT = "bot"
    CHANNEL = "channel"
    GROUP = "group"
    UNKNOWN = "unknown"

class SearchEntity(BaseModel):
    id: int
    username: Optional[str] = None
    title: str
    type: EntityType
    nft_count: int = 0
    verified: bool = False
    scam: bool = False
    fake: bool = False

class SearchResponse(BaseModel):
    query: str
    results: List[SearchEntity]

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
    owner_id: Optional[int]
    owner_name: Optional[str]
    owner_address: Optional[str]
    attributes: List[GiftAttribute]
    is_resalable: bool
    price_amount: Optional[int]
    price_currency: str