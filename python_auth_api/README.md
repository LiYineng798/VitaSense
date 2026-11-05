# VitaSense Auth API

This directory contains a standalone local-first authentication API for VitaSense.

## Endpoints

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/families`
- `GET /api/v1/families/me`
- `POST /api/v1/families/join`
- `PATCH /api/v1/families/{family_id}`
- `POST /api/v1/families/{family_id}/invite-code/regenerate`
- `DELETE /api/v1/families/{family_id}/members/{member_user_id}`
- `DELETE /api/v1/families/{family_id}/members/me`
- `POST /api/v1/families/{family_id}/status`
- `POST /api/v1/families/{family_id}/supports`

## Local Run

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Smoke Test

Start the server first, then run:

```bash
python smoke_test.py
```

The smoke test will:

- register a fresh user
- confirm duplicate registration is rejected
- log in with valid credentials
- reject an invalid password
- resolve `/me` with the bearer token returned from registration

## Deployment Notes

- The service stores data in `auth.db` in this directory.
- Accounts are independent from the Android app's current local Room storage.
- When the Android app is later connected to a deployed server, the request/response fields in this service already match the intended remote contract.

## VitaSense 2.0 Family Check-ins

Deploy the updated `main.py` alongside the existing auth/family/sync/AI service. Initialization adds `family_check_ins` and its family/time index without deleting existing tables or data.

`POST /api/v1/families/{family_id}/status` now accepts:

- `share_mood` (boolean): false clears the shared mood category and note.
- `share_check_in` (boolean): false removes the user's shared check-in history in that family.
- `check_in`: optional `ok` or `needs_support`; recorded only with check-in sharing enabled.

Check-ins use the authenticated user and server timestamp. Identical actions within one minute do not duplicate history. Only current family members may read authenticated `GET /api/v1/families/{family_id}/check-ins`, returning the latest 100 check-ins. Leaving/removing a member cascades deletion. Existing support actions remain available as acknowledgements.

The Android client excludes journal notes from all family requests and keeps contact phone/email data on-device. AI insights and reports reuse `/api/v1/ai/chat/stream`; no additional AI endpoint or provider configuration is required.

Install test dependencies with `pip install -r requirements-dev.txt`. Run `pytest -q`, the existing endpoint scripts, and `python family_check_ins_test.py`. `smoke_test.py` additionally requires a running local server on port 8000.
