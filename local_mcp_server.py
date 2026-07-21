from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import datetime
import zoneinfo

PORT = 8090

class LocalMcpHandler(BaseHTTPRequestHandler):

    def _set_headers(self, status=200):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Accept', 'application/json, text/event-stream')
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept')
        self.end_headers()

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length).decode('utf-8')
        
        try:
            req = json.loads(post_data)
        except Exception:
            self._set_headers(400)
            self.wfile.write(json.dumps({"error": "Invalid JSON"}).encode('utf-8'))
            return

        method = req.get("method")
        msg_id = req.get("id")

        if method == "tools/list":
            response = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "tools": [
                        {
                            "name": "calculate",
                            "description": "Perform mathematical calculations (addition, subtraction, multiplication, division).",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "a": {"type": "number", "description": "First number"},
                                    "b": {"type": "number", "description": "Second number"},
                                    "operation": {
                                        "type": "string",
                                        "enum": ["add", "subtract", "multiply", "divide"],
                                        "description": "Operation to perform"
                                    }
                                },
                                "required": ["a", "b", "operation"]
                            }
                        },
                        {
                            "name": "get_current_time",
                            "description": "Get current time and timezone details for any location.",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "timezone": {
                                        "type": "string",
                                        "description": "IANA Timezone name, e.g. Asia/Bangkok, UTC, America/New_York"
                                    }
                                },
                                "required": ["timezone"]
                            }
                        }
                    ]
                }
            }
            self._set_headers(200)
            self.wfile.write(json.dumps(response).encode('utf-8'))
            return

        elif method == "tools/call":
            params = req.get("params", {})
            name = params.get("name")
            arguments = params.get("arguments", {})

            if name == "calculate":
                a = float(arguments.get("a", 0))
                b = float(arguments.get("b", 0))
                op = arguments.get("operation", "add")
                
                if op == "add":
                    res = a + b
                elif op == "subtract":
                    res = a - b
                elif op == "multiply":
                    res = a * b
                elif op == "divide":
                    res = a / b if b != 0 else "Error: Division by zero"
                else:
                    res = "Invalid operation"

                result_text = f"Result of {op} on {a} and {b} is {res}"
                resp_data = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": json.dumps({"result": res, "explanation": result_text})
                            }
                        ]
                    }
                }
                self._set_headers(200)
                self.wfile.write(json.dumps(resp_data).encode('utf-8'))
                return

            elif name == "get_current_time":
                tz_str = arguments.get("timezone", "Asia/Bangkok")
                try:
                    tz = zoneinfo.ZoneInfo(tz_str)
                    now = datetime.datetime.now(tz)
                except Exception:
                    tz = zoneinfo.ZoneInfo("UTC")
                    now = datetime.datetime.now(tz)

                formatted = now.strftime("%Y-%m-%d %H:%M:%S %Z (UTC%z)")
                day_name = now.strftime("%A")

                resp_data = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": json.dumps({
                                    "timezone": tz_str,
                                    "current_time": formatted,
                                    "day_of_week": day_name
                                })
                            }
                        ]
                    }
                }
                self._set_headers(200)
                self.wfile.write(json.dumps(resp_data).encode('utf-8'))
                return

            else:
                self._set_headers(404)
                self.wfile.write(json.dumps({"jsonrpc": "2.0", "id": msg_id, "error": {"code": -32601, "message": "Method not found"}}).encode('utf-8'))
                return

        else:
            self._set_headers(400)
            self.wfile.write(json.dumps({"jsonrpc": "2.0", "id": msg_id, "error": {"code": -32600, "message": "Invalid Request"}}).encode('utf-8'))

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', PORT), LocalMcpHandler)
    print(f"🚀 Local MCP Server running on port {PORT}...")
    server.serve_forever()
