import importlib
import tempfile
from pathlib import Path
from fastapi.testclient import TestClient
from family_endpoints_test import register, auth_headers


def test_check_ins_permissions_history_privacy_and_validation():
    module = importlib.import_module('main')
    with tempfile.TemporaryDirectory() as tmp:
        module.DB_PATH = Path(tmp) / 'auth.db'
        module.initialize_database()
        client = TestClient(module.app)
        token, user = register(client, 'checkin-owner')
        outsider, _ = register(client, 'checkin-outsider')
        family = client.post('/api/v1/families', json={'name': 'Check-ins'}, headers=auth_headers(token)).json()['family']
        path = f"/api/v1/families/{family['id']}"
        payload = {'status_label': "I'm OK", 'updated_at': 1, 'share_mood': False, 'mood_type': 'LOW', 'mood_note': 'private', 'share_check_in': True, 'check_in': 'ok', 'share_health_score': False, 'health_score': 99}
        assert client.post(path + '/status', json=payload).status_code == 401
        assert client.post(path + '/status', json=payload, headers=auth_headers(outsider)).status_code == 404
        assert client.get(path + '/check-ins', headers=auth_headers(outsider)).status_code == 404
        assert client.get(path + '/check-ins').status_code == 401
        result = client.post(path + '/status', json=payload, headers=auth_headers(token))
        assert result.status_code == 200, result.text
        member = result.json()['family']['members'][0]
        assert member['mood_type'] is None and member['mood_note'] is None and member['health_score'] is None
        client.post(path + '/status', json=payload, headers=auth_headers(token))
        history = client.get(path + '/check-ins', headers=auth_headers(token)).json()['check_ins']
        assert len(history) == 1 and history[0]['check_in'] == 'ok'
        assert history[0]['created_at'] > 1 and history[0]['user_id'] == user['id']
        payload['check_in'] = 'needs_support'
        assert client.post(path + '/status', json=payload, headers=auth_headers(token)).status_code == 200
        assert len(client.get(path + '/check-ins', headers=auth_headers(token)).json()['check_ins']) == 2
        payload['check_in'] = 'invalid'
        assert client.post(path + '/status', json=payload, headers=auth_headers(token)).status_code == 422
        payload.update(check_in=None, share_check_in=False, status_label='Sharing preferences updated')
        assert client.post(path + '/status', json=payload, headers=auth_headers(token)).status_code == 200
        assert client.get(path + '/check-ins', headers=auth_headers(token)).json()['check_ins'] == []


if __name__ == '__main__':
    test_check_ins_permissions_history_privacy_and_validation()
    print('Family check-in tests passed.')
