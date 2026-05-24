<#ftl strip_whitespace=true>
<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "header">
        Grant Access
    <#elseif section = "form">
        <div class="oauth-grant-container">
            <h1>Grant Access</h1>
            <p>The application <strong>${client.clientId}</strong> is requesting access to your account.</p>
            
            <#if oauth.clientScopesRequested??>
                <div class="scopes">
                    <h3>Requested Permissions:</h3>
                    <ul>
                        <#list oauth.clientScopesRequested as requestedScope>
                            <li>${requestedScope.consentScreenText}</li>
                        </#list>
                    </ul>
                </div>
            </#if>
            
            <form action="${url.oauthAction}" method="post">
                <div class="button-group">
                    <button type="submit" id="kc-login" name="accept" class="btn-primary">
                        Allow
                    </button>
                    <button type="submit" id="kc-decline" name="cancel" class="btn-secondary">
                        Deny
                    </button>
                </div>
            </form>
        </div>
        
        <style>
            .oauth-grant-container {
                background: white;
                padding: 40px;
                border-radius: 10px;
                box-shadow: 0 14px 28px rgba(0,0,0,0.25);
                width: 400px;
                max-width: 90%;
                text-align: center;
                margin: 0 auto;
            }
            
            .oauth-grant-container h1 {
                color: #333;
                margin-bottom: 20px;
            }
            
            .oauth-grant-container p {
                color: #666;
                margin-bottom: 20px;
                font-size: 14px;
            }
            
            .scopes {
                text-align: left;
                margin: 20px 0;
                padding: 15px;
                background: #f5f5f5;
                border-radius: 5px;
            }
            
            .scopes h3 {
                margin-top: 0;
                color: #333;
            }
            
            .scopes ul {
                margin: 10px 0;
                padding-left: 20px;
            }
            
            .scopes li {
                margin-bottom: 5px;
                color: #666;
            }
            
            .button-group {
                margin-top: 30px;
            }
            
            .btn-primary {
                background: #FF4B2B;
                color: white;
                border: none;
                padding: 12px 30px;
                border-radius: 20px;
                margin-right: 10px;
                cursor: pointer;
            }
            
            .btn-secondary {
                background: #666;
                color: white;
                border: none;
                padding: 12px 30px;
                border-radius: 20px;
                cursor: pointer;
            }
        </style>
    </#if>
</@layout.registrationLayout>