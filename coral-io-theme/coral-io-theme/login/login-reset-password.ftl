<#ftl strip_whitespace=true>
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username'); section>
    <#if section = "header">
        Réinitialisation du mot de passe
    <#elseif section = "form">
        <div class="reset-password-container">
            <h1>Réinitialiser votre mot de passe</h1>
            <p>Saisissez votre nom d'utilisateur ou votre email pour recevoir les instructions de réinitialisation.</p>
            
            <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
                
                <!-- ===== MESSAGES SPÉCIFIQUES ===== -->
                <#-- Si email envoyé avec succès -->
                <#if message?? && message.type == 'success'>
                    <div class="alert alert-success">
                        Un email a été envoyé pour réinitialiser votre mot de passe. Veuillez vérifier votre boîte de réception.
                    </div>
                <#elseif message?? && message.type == 'error'>
                    <#-- Username/email incorrect -->
                    <#if messagesPerField.existsError('username')>
                        <div class="alert alert-error">
                            Ce nom d'utilisateur ou email n'existe pas.
                        </div>
                    <#else>
                        <div class="alert alert-error">
                             Échec de l'envoi de l'email de réinitialisation. Veuillez réessayer plus tard.
                        </div>
                    </#if>
                </#if>
                <!-- ===== FIN MESSAGES ===== -->

                <input type="text" 
                       id="username" 
                       name="username" 
                       value="${(auth.attemptedUsername!'')}" 
                       placeholder="Username or Email" 
                       autofocus
                       aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"/>
                <br>
                <button type="submit">Envoyer</button>
                
                <div class="back-to-login">
                    <a href="${url.loginUrl}">Retour à la connexion</a>
                </div>
            </form>
        </div>

        <style>
            .reset-password-container {
                background: white;
                padding: 40px;
                border-radius: 10px;
                box-shadow: 0 14px 28px rgba(0,0,0,0.25);
                width: 400px;
                max-width: 90%;
                text-align: center;
                margin: 0 auto;
            }
            
            .reset-password-container h1 {
                color: #333;
                margin-bottom: 10px;
            }
            
            .reset-password-container p {
                color: #666;
                margin-bottom: 30px;
                font-size: 14px;
            }

            .alert {
                padding: 12px 20px;
                margin-bottom: 20px;
                border-radius: 5px;
                font-size: 14px;
            }

            .alert-success {
                background-color: rgba(76, 175, 80, 0.1);
                color: #4CAF50;
                border: 1px solid #4CAF50;
            }

            .alert-error {
                background-color: rgba(255, 75, 43, 0.1);
                color: #FF4B2B;
                border: 1px solid #FF4B2B;
            }
            
            .back-to-login {
                margin-top: 20px;
                font-size: 14px;
            }
            
            .back-to-login a {
                color: #FF4B2B;
                text-decoration: none;
            }
            
            .back-to-login a:hover {
                text-decoration: underline;
            }
        </style>
    </#if>
</@layout.registrationLayout>
