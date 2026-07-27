from http.server import HTTPServer, BaseHTTPRequestHandler
import json

PORT = 9999

class LegacyRestHandler(BaseHTTPRequestHandler):
    def _set_headers(self, status=200):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(200)

    def do_GET(self):
        self._set_headers(200)
        resp = {
            "status": "ONLINE",
            "service": "Legacy Order & Shipping REST API",
            "message": "Send POST request with parameters to /api/legacy"
        }
        self.wfile.write(json.dumps(resp, indent=2).encode('utf-8'))

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        body_str = self.rfile.read(content_length).decode('utf-8')
        
        try:
            req_data = json.loads(body_str)
        except Exception:
            req_data = {}

        # Support both raw REST body OR JSON-RPC params wrapper
        args = req_data
        if "params" in req_data and isinstance(req_data["params"], dict):
            args = req_data["params"].get("arguments", req_data["params"])

        order_id = args.get("order_id") or req_data.get("order_id")
        amount = args.get("amount") or req_data.get("amount")

        response_payload = {}

        if order_id:
            response_payload = {
                "order_id": str(order_id),
                "status": "SHIPPED",
                "carrier": "Kerry Express",
                "tracking_number": "KER-99887711-TH",
                "estimated_delivery": "2026-07-28 14:00:00"
            }
        elif amount is not None:
            try:
                amt = float(amount)
                vat_7_pct = round(amt * 0.07, 2)
                response_payload = {
                    "subtotal": amt,
                    "vat_rate": "7%",
                    "vat_amount": vat_7_pct,
                    "total_amount": round(amt + vat_7_pct, 2)
                }
            except Exception:
                response_payload = {"error": "Invalid amount number"}
        else:
            response_payload = {
                "service": "Legacy Payment & Order REST API",
                "received_request": req_data,
                "note": "Processed successfully by legacy REST backend"
            }

        # Wrap in JSON-RPC style content if expected or plain JSON
        resp = {
            "jsonrpc": "2.0",
            "id": req_data.get("id", 1),
            "result": {
                "content": [
                    {
                        "type": "text",
                        "text": json.dumps(response_payload, ensure_ascii=False)
                    }
                ]
            }
        }
        self._set_headers(200)
        self.wfile.write(json.dumps(resp).encode('utf-8'))

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', PORT), LegacyRestHandler)
    print(f"🚀 Legacy REST Mock Server running on port {PORT}...")
    server.serve_forever()
