# VitaSense Auth API

This directory contains a standalone local-first authentication API for VitaSense.

## Endpoints

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

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
