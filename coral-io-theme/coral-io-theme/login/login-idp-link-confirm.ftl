<#ftl strip_whitespace=true>
<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "header">
        Link Account
    <#elseif section = "form">
        <div class="idp-link-container">
            <h1>Link Accounts</h1>
            <p>You are already logged in as ${auth.loggedInUser.username}.</p>
            <p>Do you want to link your account with ${idpDisplayName}?</p>
            
            <form action="${url.loginAction}" method="post">
                <div class="button-group">
                    <button type="submit" name="submitAction" value="LinkAccount" class="btn-primary">
                        Link Accounts
                    </button>
                    <button type="submit" name="submitAction" value="Cancel" class="btn-secondary">
                        Cancel
                    </button>
                </div>
            </form>
        </div>
        
        <style>
            .idp-link-container {
                background: white;
                padding: 40px;
                border-radius: 10px;
                box-shadow: 0 14px 28px rgba(0,0,0,0.25);
                width: 400px;
                max-width: 90%;
                text-align: center;
                margin: 0 auto;
            }
            
            .idp-link-container h1 {
                color: #333;
                margin-bottom: 20px;
            }
            
            .idp-link-container p {
                color: #666;
                margin-bottom: 15px;
                font-size: 14px;
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