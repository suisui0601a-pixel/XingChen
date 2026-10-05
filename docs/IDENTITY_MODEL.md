# Identity model

## Keys

* `Person`: unique external identity `(platform, platform_user_id)`. A mutable display name never determines identity.
* `Conversation`: unique `(platform, type, platform_conversation_id)`.
* `Membership`: one record for a person in a conversation, with current display name/card, platform role and first/last seen timestamps.
* `PersonAlias`: observed name/card plus source conversation and first/last-seen timestamps.

The process-local registry uses deterministic IDs derived from platform identity; `IncomingMessageStore` transactionally writes person, conversation, membership and alias observations with the event. SQLite unique keys enforce identity and message idempotency. Owner identifiers are supplied by configuration; SELF (the bot), OWNER (the administrator) and MEMBER are distinct. GROUP_ADMIN and GROUP_OWNER are platform membership roles.

## Unified platform events

`PlatformEvent` carries `Platform`, `ConversationIdentity`, `ActorIdentity`, `MessageIdentity`, optional reply/reference, timestamp, text and raw metadata. The latter is retained only for forward compatibility and diagnosis. Adapters must translate transport identifiers before domain logic.

## Contract coverage

ID-001 different QQ IDs with the same display name remain distinct; ID-002 a rename preserves person identity and records aliases; ID-003 one QQ user has separate memberships in separate groups; ID-004 the configured bot ID is SELF; ID-005 owner and member flags remain distinct. `GROUP_ADMIN` and `GROUP_OWNER` are represented as `IdentityRole`, not prompt text.
