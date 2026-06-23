<#ftl strip_whitespace=true>
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
    <#if section = "header">
        Mettre à jour le mot de passe
    <#elseif section = "form">
        <div class="update-password-container">
            <h1>Créer un nouveau mot de passe</h1>
            <p>Votre nouveau mot de passe doit être différent de vos mots de passe précédents.</p>
            
            <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">
                <#if messagesPerField.existsError('password','password-confirm')>
                    <div class="alert alert-error">
                        ${kcSanitize(messagesPerField.getFirstError('password','password-confirm'))?no_esc}
                    </div>
                </#if>

                <input type="password" 
                       id="password-new" 
                       name="password-new" 
                       placeholder="Nouveau mot de passe" 
                       autofocus
                       autocomplete="new-password"
                       aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"/>

                <input type="password" 
                       id="password-confirm" 
                       name="password-confirm" 
                       placeholder="Confirmez votre nouveau mot de passe"
                       autocomplete="new-password"
                       aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"/>

                <button type="submit">Mettre à jour</button>
            </form>
        </div>
        
        <style>
            .update-password-container {
                background: white;
                padding: 40px;
                border-radius: 10px;
                box-shadow: 0 14px 28px rgba(0,0,0,0.25);
                width: 400px;
                max-width: 90%;
                text-align: center;
                margin: 0 auto;
            }
            
            .update-password-container h1 {
                color: #333;
                margin-bottom: 10px;
            }
            
            .update-password-container p {
                color: #666;
                margin-bottom: 30px;
                font-size: 14px;
            }
        </style>
    </#if>
</@layout.registrationLayout>