from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import datetime
import zoneinfo
import hashlib
import uuid
import re
import random
import string
import statistics
import urllib.parse
import base64
import csv
import io
import difflib

PORT = 8090

TOOLS_DEFINITION = [
    {
        "name": "calculate",
        "description": "Perform basic mathematical calculations (add, subtract, multiply, divide).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "a": {"type": "number", "description": "First number"},
                "b": {"type": "number", "description": "Second number"},
                "operation": {"type": "string", "enum": ["add", "subtract", "multiply", "divide"]}
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
                "timezone": {"type": "string", "description": "IANA Timezone name, e.g. Asia/Bangkok, UTC, America/New_York"}
            },
            "required": ["timezone"]
        }
    },
    {
        "name": "count_text_stats",
        "description": "Count words, characters, lines, and sentences in a given text.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "The target text to analyze"}
            },
            "required": ["text"]
        }
    },
    {
        "name": "convert_unit",
        "description": "Convert measurement units (temperature, distance, weight, data size).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "value": {"type": "number", "description": "Numeric value to convert"},
                "category": {"type": "string", "enum": ["temperature", "distance", "weight", "data_size"]},
                "from_unit": {"type": "string", "description": "e.g., C, F, km, miles, kg, lbs, MB, GB"},
                "to_unit": {"type": "string", "description": "e.g., C, F, km, miles, kg, lbs, MB, GB"}
            },
            "required": ["value", "category", "from_unit", "to_unit"]
        }
    },
    {
        "name": "date_difference",
        "description": "Calculate difference between two dates in days, weeks, and months.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "start_date": {"type": "string", "description": "YYYY-MM-DD"},
                "end_date": {"type": "string", "description": "YYYY-MM-DD"}
            },
            "required": ["start_date", "end_date"]
        }
    },
    {
        "name": "generate_uuid",
        "description": "Generate a unique random UUID (v4).",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "generate_password",
        "description": "Generate a secure random password.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "length": {"type": "integer", "description": "Length of password (default 16)"},
                "include_symbols": {"type": "boolean", "description": "Include special characters"}
            }
        }
    },
    {
        "name": "calculate_stats",
        "description": "Calculate statistical metrics (mean, median, mode, min, max, sum, std_dev) for a list of numbers.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "numbers": {
                    "type": "array",
                    "items": {"type": "number"},
                    "description": "List of numbers to calculate statistics for"
                }
            },
            "required": ["numbers"]
        }
    },
    {
        "name": "calculate_loan_pmt",
        "description": "Calculate monthly loan / mortgage payment (PMT), total interest, and total payment.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "principal": {"type": "number", "description": "Total loan principal amount"},
                "annual_rate_percent": {"type": "number", "description": "Annual interest rate percentage (e.g. 5.5 for 5.5%)"},
                "term_years": {"type": "integer", "description": "Loan term in years"}
            },
            "required": ["principal", "annual_rate_percent", "term_years"]
        }
    },
    {
        "name": "regex_extract",
        "description": "Extract structured entities (emails, phone numbers, IP addresses, URLs) from text via Regex.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "Raw text content"},
                "extract_type": {
                    "type": "string",
                    "enum": ["email", "phone", "ip", "url", "all"],
                    "description": "Entity type to extract"
                }
            },
            "required": ["text", "extract_type"]
        }
    },
    {
        "name": "strip_html",
        "description": "Strip HTML tags and extract clean plain text content.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "html_content": {"type": "string", "description": "Raw HTML string"}
            },
            "required": ["html_content"]
        }
    },
    {
        "name": "parse_url",
        "description": "Parse URL components (scheme, domain, port, path, query parameters).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "url": {"type": "string", "description": "Target URL string"}
            },
            "required": ["url"]
        }
    },
    {
        "name": "base64_encode_decode",
        "description": "Base64 encode or decode text strings.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "Text to encode/decode"},
                "operation": {"type": "string", "enum": ["encode", "decode"]}
            },
            "required": ["text", "operation"]
        }
    },
    {
        "name": "url_encode_decode",
        "description": "URL encode or decode strings (e.g. handle Special characters & Thai text).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "String to encode/decode"},
                "operation": {"type": "string", "enum": ["encode", "decode"]}
            },
            "required": ["text", "operation"]
        }
    },
    {
        "name": "csv_to_json",
        "description": "Parse raw CSV text into a JSON array of objects.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "csv_text": {"type": "string", "description": "Raw CSV string with header row"}
            },
            "required": ["csv_text"]
        }
    },
    {
        "name": "text_diff",
        "description": "Compare differences between two text strings and return added/removed lines.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "original_text": {"type": "string", "description": "Original text"},
                "modified_text": {"type": "string", "description": "Modified text"}
            },
            "required": ["original_text", "modified_text"]
        }
    }
]

class LocalMcpHandler(BaseHTTPRequestHandler):

    def _set_headers(self, status=200, content_type='application/json'):
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept')
        self.send_header('Accept', 'application/json, text/event-stream')
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(200)

    def do_GET(self):
        parsed_path = urllib.parse.urlparse(self.path).path
        if parsed_path == "/mcp":
            self._set_headers(200, 'application/json')
            resp = {
                "status": "RUNNING",
                "message": "PP-GPT Local MCP Server Endpoint. Send HTTP POST JSON-RPC 2.0 requests to /mcp",
                "endpoint": "/mcp",
                "registered_tools_count": len(TOOLS_DEFINITION)
            }
            self.wfile.write(json.dumps(resp, indent=2).encode('utf-8'))
            return
        
        # Default Welcome Page for root /
        self._set_headers(200, 'text/html; charset=utf-8')
        tools_html = "".join([
            f'<div class="tool-card"><h3>⚡ {t["name"]}</h3><p>{t["description"]}</p></div>'
            for t in TOOLS_DEFINITION
        ])
        html = f"""<!DOCTYPE html>
<html>
<head>
    <title>PP-GPT Local MCP Server</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f1017; color: #e2e8f0; margin: 0; padding: 40px 20px; display: flex; justify-content: center; }}
        .container {{ max-width: 900px; width: 100%; }}
        .header {{ background: #1a1b26; border: 1px solid #2e3047; padding: 30px; border-radius: 20px; text-align: center; margin-bottom: 30px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }}
        .badge {{ background: #10b981; color: #042f2e; padding: 4px 12px; border-radius: 99px; font-weight: bold; font-size: 12px; display: inline-block; margin-bottom: 15px; }}
        h1 {{ margin: 0 0 10px 0; color: #fff; font-size: 26px; }}
        p {{ color: #94a3b8; font-size: 14px; margin: 5px 0; }}
        .endpoint-box {{ background: #111219; border: 1px border-[#3b82f6]; padding: 12px 20px; border-radius: 12px; font-family: monospace; color: #38bdf8; font-size: 15px; margin-top: 15px; display: inline-block; }}
        .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 15px; }}
        .tool-card {{ background: #1a1b26; border: 1px solid #2e3047; padding: 18px; border-radius: 16px; transition: transform 0.2s; }}
        .tool-card:hover {{ transform: translateY(-3px); border-color: #3b82f6; }}
        .tool-card h3 {{ margin: 0 0 8px 0; color: #ffd700; font-size: 15px; }}
        .tool-card p {{ margin: 0; color: #cbd5e1; font-size: 12px; line-height: 1.4; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="badge">🟢 ONLINE & READY</div>
            <h1>🤖 PP-GPT Local MCP Server</h1>
            <p>Model Context Protocol (MCP) Server for LLM Tool Calling</p>
            <div class="endpoint-box">MCP Endpoint: POST http://localhost:{PORT}/mcp</div>
        </div>
        <h2 style="font-size: 18px; color: #fff; margin-bottom: 15px;">🛠️ Registered Baseline Tools ({len(TOOLS_DEFINITION)})</h2>
        <div class="grid">
            {tools_html}
        </div>
    </div>
</body>
</html>"""
        self.wfile.write(html.encode('utf-8'))

    def do_POST(self):
        parsed_path = urllib.parse.urlparse(self.path).path
        if parsed_path not in ["/mcp", "/", ""]:
            self._set_headers(404)
            self.wfile.write(json.dumps({"error": "Endpoint not found. Send POST to /mcp"}).encode('utf-8'))
            return

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
                "result": {"tools": TOOLS_DEFINITION}
            }
            self._set_headers(200)
            self.wfile.write(json.dumps(response).encode('utf-8'))
            return

        elif method == "tools/call":
            params = req.get("params", {})
            name = params.get("name")
            arguments = params.get("arguments", {})

            res_payload = {}

            if name == "calculate":
                a, b, op = float(arguments.get("a", 0)), float(arguments.get("b", 0)), arguments.get("operation")
                res = a + b if op == "add" else a - b if op == "subtract" else a * b if op == "multiply" else (a / b if b != 0 else "Error: Division by zero")
                res_payload = {"result": res, "explanation": f"{op} on {a} and {b} = {res}"}

            elif name == "get_current_time":
                tz_str = arguments.get("timezone", "Asia/Bangkok")
                try: tz = zoneinfo.ZoneInfo(tz_str)
                except Exception: tz = zoneinfo.ZoneInfo("UTC")
                now = datetime.datetime.now(tz)
                res_payload = {"timezone": tz_str, "current_time": now.strftime("%Y-%m-%d %H:%M:%S %Z (UTC%z)"), "day_of_week": now.strftime("%A")}

            elif name == "count_text_stats":
                text = arguments.get("text", "")
                words = len(re.findall(r'\w+', text))
                lines = len(text.splitlines())
                res_payload = {"char_count_total": len(text), "char_count_no_spaces": len(text.replace(" ", "")), "word_count": words, "line_count": lines}

            elif name == "date_difference":
                try:
                    d1 = datetime.datetime.strptime(arguments.get("start_date"), "%Y-%m-%d")
                    d2 = datetime.datetime.strptime(arguments.get("end_date"), "%Y-%m-%d")
                    diff_days = abs((d2 - d1).days)
                    res_payload = {"days": diff_days, "weeks": round(diff_days / 7, 2), "approx_months": round(diff_days / 30.44, 2)}
                except Exception as e:
                    res_payload = {"error": f"Invalid date format (use YYYY-MM-DD): {str(e)}"}

            elif name == "generate_uuid":
                res_payload = {"uuid": str(uuid.uuid4())}

            elif name == "generate_password":
                length = int(arguments.get("length", 16))
                symbols = arguments.get("include_symbols", True)
                chars = string.ascii_letters + string.digits + (string.punctuation if symbols else "")
                password = "".join(random.choice(chars) for _ in range(length))
                res_payload = {"password": password, "length": length}

            elif name == "convert_unit":
                val = float(arguments.get("value", 0))
                cat = arguments.get("category", "distance")
                fu = str(arguments.get("from_unit", "")).upper().strip()
                tu = str(arguments.get("to_unit", "")).upper().strip()
                converted = val
                
                # Normalize unit aliases
                if fu in ["INCH", "INCHES", "IN"]: fu = "INCH"
                if tu in ["INCH", "INCHES", "IN"]: tu = "INCH"
                if fu in ["CENTIMETER", "CENTIMETERS", "CM"]: fu = "CM"
                if tu in ["CENTIMETER", "CENTIMETERS", "CM"]: tu = "CM"
                if fu in ["METER", "METERS", "M"]: fu = "M"
                if tu in ["METER", "METERS", "M"]: tu = "M"
                if fu in ["KILOMETER", "KILOMETERS", "KM"]: fu = "KM"
                if tu in ["KILOMETER", "KILOMETERS", "KM"]: tu = "KM"
                if fu in ["MILE", "MILES"]: fu = "MILES"
                if tu in ["MILE", "MILES"]: tu = "MILES"
                if fu in ["FOOT", "FEET", "FT"]: fu = "FT"
                if tu in ["FOOT", "FEET", "FT"]: tu = "FT"

                if cat == "temperature" or fu in ["C", "F", "K"]:
                    if fu == "C" and tu == "F": converted = (val * 9/5) + 32
                    elif fu == "F" and tu == "C": converted = (val - 32) * 5/9
                    elif fu == "C" and tu == "K": converted = val + 273.15
                    elif fu == "K" and tu == "C": converted = val - 273.15
                elif cat == "weight" or fu in ["KG", "LBS", "G"]:
                    if fu == "KG" and tu == "LBS": converted = val * 2.20462
                    elif fu == "LBS" and tu == "KG": converted = val / 2.20462
                    elif fu == "KG" and tu == "G": converted = val * 1000
                    elif fu == "G" and tu == "KG": converted = val / 1000
                elif cat == "distance" or fu in ["INCH", "CM", "M", "KM", "MILES", "FT"]:
                    meters = val
                    if fu == "INCH": meters = val * 0.0254
                    elif fu == "CM": meters = val / 100.0
                    elif fu == "M": meters = val
                    elif fu == "KM": meters = val * 1000.0
                    elif fu == "MILES": meters = val * 1609.344
                    elif fu == "FT": meters = val * 0.3048

                    if tu == "INCH": converted = meters / 0.0254
                    elif tu == "CM": converted = meters * 100.0
                    elif tu == "M": converted = meters
                    elif tu == "KM": converted = meters / 1000.0
                    elif tu == "MILES": converted = meters / 1609.344
                    elif tu == "FT": converted = meters / 0.3048
                elif cat == "data_size" or fu in ["B", "KB", "MB", "GB", "TB"]:
                    if fu == "MB" and tu == "GB": converted = val / 1024
                    elif fu == "GB" and tu == "MB": converted = val * 1024
                    elif fu == "KB" and tu == "MB": converted = val / 1024
                    elif fu == "GB" and tu == "TB": converted = val / 1024

                res_payload = {"value": val, "from": fu, "to": tu, "result": round(converted, 4)}

            elif name == "calculate_stats":
                nums = [float(n) for n in arguments.get("numbers", [])]
                if not nums:
                    res_payload = {"error": "Empty numbers array"}
                else:
                    mode_val = statistics.mode(nums) if len(nums) > 0 else None
                    std_val = statistics.stdev(nums) if len(nums) > 1 else 0.0
                    res_payload = {
                        "count": len(nums),
                        "sum": sum(nums),
                        "mean": round(statistics.mean(nums), 4),
                        "median": round(statistics.median(nums), 4),
                        "mode": mode_val,
                        "min": min(nums),
                        "max": max(nums),
                        "std_dev": round(std_val, 4)
                    }

            elif name == "calculate_loan_pmt":
                principal = float(arguments.get("principal", 0))
                rate_pct = float(arguments.get("annual_rate_percent", 0))
                years = int(arguments.get("term_years", 0))
                if principal <= 0 or rate_pct <= 0 or years <= 0:
                    res_payload = {"error": "Invalid principal, rate, or term"}
                else:
                    r = (rate_pct / 100.0) / 12.0
                    n = years * 12
                    pmt = (principal * r * (1 + r)**n) / ((1 + r)**n - 1)
                    total_payment = pmt * n
                    total_interest = total_payment - principal
                    res_payload = {
                        "principal": principal,
                        "monthly_payment": round(pmt, 2),
                        "total_payment": round(total_payment, 2),
                        "total_interest": round(total_interest, 2),
                        "total_months": n
                    }

            elif name == "regex_extract":
                text = arguments.get("text", "")
                e_type = arguments.get("extract_type", "all")
                emails = re.findall(r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}', text)
                phones = re.findall(r'\+?\d{1,4}?[-.\s]?\(?\d{1,3}?\)?[-.\s]?\d{1,4}[-.\s]?\d{1,4}[-.\s]?\d{1,9}', text)
                ips = re.findall(r'\b(?:\d{1,3}\.){3}\d{1,3}\b', text)
                urls = re.findall(r'https?://[^\s<>"]+', text)
                
                if e_type == "email": res_payload = {"emails": list(set(emails))}
                elif e_type == "phone": res_payload = {"phones": list(set(phones))}
                elif e_type == "ip": res_payload = {"ips": list(set(ips))}
                elif e_type == "url": res_payload = {"urls": list(set(urls))}
                else: res_payload = {"emails": list(set(emails)), "phones": list(set(phones)), "ips": list(set(ips)), "urls": list(set(urls))}

            elif name == "strip_html":
                html = arguments.get("html_content", "")
                clean_text = re.sub(r'<[^>]+>', '', html).strip()
                res_payload = {"clean_text": clean_text}

            elif name == "parse_url":
                raw_url = arguments.get("url", "")
                parsed = urllib.parse.urlparse(raw_url)
                params = urllib.parse.parse_qs(parsed.query)
                res_payload = {
                    "scheme": parsed.scheme,
                    "hostname": parsed.hostname,
                    "port": parsed.port,
                    "path": parsed.path,
                    "query_params": {k: v[0] if len(v) == 1 else v for k, v in params.items()}
                }

            elif name == "base64_encode_decode":
                text = arguments.get("text", "")
                op = arguments.get("operation", "encode")
                try:
                    if op == "encode":
                        res_payload = {"result": base64.b64encode(text.encode('utf-8')).decode('utf-8')}
                    else:
                        res_payload = {"result": base64.b64decode(text.encode('utf-8')).decode('utf-8')}
                except Exception as e:
                    res_payload = {"error": f"Base64 error: {str(e)}"}

            elif name == "url_encode_decode":
                text = arguments.get("text", "")
                op = arguments.get("operation", "encode")
                if op == "encode":
                    res_payload = {"result": urllib.parse.quote(text)}
                else:
                    res_payload = {"result": urllib.parse.unquote(text)}

            elif name == "csv_to_json":
                csv_text = arguments.get("csv_text", "")
                try:
                    reader = csv.DictReader(io.StringIO(csv_text.strip()))
                    res_payload = {"rows": list(reader)}
                except Exception as e:
                    res_payload = {"error": f"CSV parse error: {str(e)}"}

            elif name == "text_diff":
                orig = arguments.get("original_text", "").splitlines()
                mod = arguments.get("modified_text", "").splitlines()
                diff = list(difflib.unified_diff(orig, mod, fromfile='original', tofile='modified', lineterm=''))
                res_payload = {"diff": diff}

            else:
                self._set_headers(404)
                self.wfile.write(json.dumps({"jsonrpc": "2.0", "id": msg_id, "error": {"code": -32601, "message": "Tool not found"}}).encode('utf-8'))
                return

            resp_data = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {"content": [{"type": "text", "text": json.dumps(res_payload)}]}
            }
            self._set_headers(200)
            self.wfile.write(json.dumps(resp_data).encode('utf-8'))
            return

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', PORT), LocalMcpHandler)
    print(f"🚀 Local MCP Server running on port {PORT} with /mcp endpoint...")
    server.serve_forever()
