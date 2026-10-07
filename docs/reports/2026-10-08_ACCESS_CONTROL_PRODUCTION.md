# Access-control production incident — sanitized report

Status: `FIXED`

## Symptom

Private chat worked, while users in the intended QQ group could not wake the bot. The runtime emitted `DEFAULT_DENY` decisions for group traffic. The Console page did not show a saved target-group rule because the production rule table was empty.

## Root cause

The target group did not have a persisted rule equivalent to:

```text
scope = GROUP
platform = QQ
effect = ALLOW
stable_id = <target group ID>
```

Group access is evaluated against the normalized group/conversation ID, not the group member's user ID.

## Correction

The target group allow rule was saved through the authenticated Console API. The effective-policy preview then returned:

```text
GROUP_ALLOW
```

Observed verification:

- group mention flow worked;
- reply continuation in the allowed group worked;
- page reload preserved/displayed the rule;
- Core restart preserved the rule;
- Core remained healthy with stable restart count;
- OneBot remained connected;
- policy preview for an unallowed group remained denied.

A real message from an unallowed group was not required for this incident closure; the fail-closed preview was verified.

## Public code follow-up

The Console client is being hardened so that clearing all currently persisted access rules requires an additional explicit confirmation. This reduces the chance of accidental full-rule replacement with an empty list.

No account IDs, group IDs, credentials, prompt content or infrastructure endpoints are included here.
