# Ticktit - Unique Features - v3 Final

1. Group-First Booking: BookMyShow books alone, then tells friends. Ticktit creates GROUP first, then books together. Group has Code TICK12 + Share Link + Leader + Members. Group is main object, not hack.
2. Split Payment Native: BookMyShow one person pays full, then asks money on WhatsApp. Ticktit each pays own share at booking time. No money chasing. Payment split by members count, each gets own Pay button.
3. Live Collaborative Seat Map: BookMyShow seat map for 1 person. Ticktit all members see SAME live map updating in real-time. WebSocket / Polling + Redis. When Leader selects A1, all see A1 turn Yellow instantly.
4. Seat Lock with 5-Min Timer: Explicit lock: "Locked for 5 min, Timer 04:32 left" + Auto-release. Redis TTL 300 sec, Key lock:show:{id}:seat:{no}. Transparent, fair.
5. WhatsApp-First Onboarding: BookMyShow needs app install. Ticktit member clicks WhatsApp link -> Joins in 30 sec, no install, no account needed to view, account needed only to pay. Viral via WhatsApp.
6. Pay For All + Split Pay in Same Booking: Friends use Split, Family uses Pay For All. Leader can switch to Pay For All at any time before timer expires.
7. Payment Safety: Idempotency-Key prevents double charge. Redis + DB Unique Constraint prevents double booking. 0 Double Charge Guarantee.
8. Group Payment Dashboard: Leader sees Who PAID Green, Who PENDING Yellow, Remind button. No "I already paid" confusion.
Future Phase 2: Poll Vote for Movie/Time, Group Chat, Seat Preference aisle/corner auto-suggest, Split Food Popcorn + Cold Drink.
