# Phase 2 memory retrieval and candidate policy

`MemoryRetriever` merges repository candidates and ranks deterministically using subject/person relevance, conversation scope, type/importance/recency and text relevance, then clips to a hard token budget. Retrieval always receives `RequestContext` and applies `MemoryVisibility`; group/conversation memories cannot leak into another conversation. This phase uses text/metadata signals only; there are no embeddings.

`MemoryPolicy` validates model-proposed candidates before persistence. A candidate is bound to the current actor; mismatched subjects and unauthorized owner-global scope are rejected. Owner-authorized project/global decisions may retain broad scope; member project/global candidates are downgraded to the current conversation. Conversation scope remains isolated even from owner requests in other conversations. Accepted long-term memories require provenance; source actor/message/conversation are recorded.

The retrieval ranking is a conservative foundation, not a learned preference system. No automatic deletion or broad global-memory promotion occurs.
