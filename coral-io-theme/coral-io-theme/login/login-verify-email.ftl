<#ftl strip_whitespace=true>
<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "header">
        Verify Email
    <#elseif section = "form">
        <div class="verify-email-container">
            <h1>Email Verification</h1>
            <p>We've sent an email with a verification link to your email address.</p>
            <p>Please check your inbox and click the link to verify your email.</p>
            
            <div class="actions">
                <a href="${url.loginUrl}" class="btn-back">Back to Login</a>
            </div>
        </div>
        
        <style>
            .verify-email-container {
                background: white;
                padding: 40px;
                border-radius: 10px;
                box-shadow: 0 14px 28px rgba(0,0,0,0.25);
                width: 400px;
                max-width: 90%;
                text-align: center;
                margin: 0 auto;
            }
            
            .verify-email-container h1 {
                color: #333;
                margin-bottom: 20px;
            }
            
            .verify-email-container p {
                color: #666;
                margin-bottom: 15px;
                font-size: 14px;
            }
            
            .actions {
                margin-top: 30px;
            }
            
            .btn-back {
                background: #FF4B2B;
                color: white;
                border: none;
                padding: 12px 30px;
                border-radius: 20px;
                text-decoration: none;
                display: inline-block;
                font-size: 14px;
            }
        </style>
    </#if>
</@layout.registrationLayout>