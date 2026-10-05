from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class DeploymentContracts(unittest.TestCase):
    def test_default_loopback_and_no_gateway_bundle(self):
        compose = (ROOT / "compose.yml").read_text()
        self.assertIn('127.0.0.1:3200:3200', compose)
        self.assertIn('read_only: true', compose)
        self.assertIn('no-new-privileges:true', compose)
        self.assertNotIn('snowluma:', compose.lower())

    def test_staging_internal_tls_no_acme_or_production_ports(self):
        config = (ROOT / "deploy/Caddyfile.staging").read_text()
        self.assertIn('tls internal', config)
        self.assertIn('13220', config)
        self.assertIn('13221', config)
        self.assertNotIn('Strict-Transport-Security', config)
        self.assertIn('header_up -Forwarded', config)

    def test_application_does_not_trust_forwarded_headers(self):
        config = (ROOT / "src/main/resources/application.yml").read_text()
        self.assertIn('forward-headers-strategy: none', config)

    def test_high_port_caddy_image_drops_upstream_file_capability(self):
        config = (ROOT / "deploy/Caddy.Dockerfile").read_text()
        self.assertIn('FROM caddy:2.10.2-alpine', config)
        self.assertIn('setcap -r /usr/bin/caddy', config)
        self.assertIn('test -z "$(getcap /usr/bin/caddy)"', config)

if __name__ == '__main__':
    unittest.main()
