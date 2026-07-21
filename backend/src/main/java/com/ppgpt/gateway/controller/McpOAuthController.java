package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/mcp/oauth")
@RequiredArgsConstructor
public class McpOAuthController {

    private final McpServerService mcpServerService;

    /**
     * OAuth callback endpoint invoked by external OAuth Providers after user approves authorization in popup.
     * Renders a clean HTML response that posts the result back to window.opener and closes the popup.
     */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> handleOAuthCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {

        log.info("[MCP OAuth] Callback received state={}, code_present={}, error={}", state, (code != null), error);

        if (error != null) {
            String msg = (errorDescription != null) ? errorDescription : error;
            return Mono.just(renderPopupResponse(false, "OAuth Authorization Failed: " + msg, state, null));
        }

        if (code == null || code.isBlank()) {
            return Mono.just(renderPopupResponse(false, "Missing authorization code from OAuth provider", state, null));
        }

        // Save mock access token or process token exchange
        String serverId = state; // state carries serverId
        if (serverId != null && !serverId.isBlank()) {
            return mcpServerService.saveOAuthTokens(serverId, "oauth_token_" + code, "refresh_token_" + code, 3600L)
                    .map(dto -> renderPopupResponse(true, "Successfully connected to MCP Server!", serverId, code))
                    .onErrorResume(e -> Mono.just(renderPopupResponse(false, "Failed to save OAuth tokens: " + e.getMessage(), serverId, null)));
        }

        return Mono.just(renderPopupResponse(true, "OAuth Authorization Granted!", state, code));
    }

    private String renderPopupResponse(boolean success, String message, String serverId, String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>MCP OAuth Authorization</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #111219; color: #fff; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    .card { background: #1a1b26; border: 1px solid #2e3047; padding: 30px; border-radius: 16px; text-align: center; max-width: 400px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                    .icon { font-size: 48px; margin-bottom: 16px; }
                    h2 { margin: 0 0 10px 0; font-size: 18px; }
                    p { color: #9ca3af; font-size: 13px; margin: 0 0 20px 0; }
                    button { background: #ffd700; color: #1a1b22; border: none; padding: 10px 20px; font-weight: bold; border-radius: 8px; cursor: pointer; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">%s</div>
                    <h2>%s</h2>
                    <p>%s</p>
                    <button onclick="window.close()">Close Window</button>
                </div>
                <script>
                    if (window.opener) {
                        window.opener.postMessage({
                            type: 'MCP_OAUTH_RESPONSE',
                            success: %b,
                            message: "%s",
                            serverId: "%s",
                            code: "%s"
                        }, '*');
                    }
                    setTimeout(function() { window.close(); }, 3000);
                </script>
            </body>
            </html>
            """.formatted(
                success ? "✅" : "❌",
                success ? "Authorization Successful" : "Authorization Failed",
                message,
                success,
                message,
                serverId != null ? serverId : "",
                code != null ? code : ""
        );
    }
}
