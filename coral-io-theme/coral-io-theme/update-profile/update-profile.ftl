<#ftl strip_whitespace=true>
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email'); section>
    <#if section = "form">
        <form id="kc-update-profile-form" action="${url.loginAction}" method="post">
            <h1>Update Profile</h1>
            
            <#if messagesPerField.existsError('firstName','lastName','email')>
                <div class="alert alert-error">
                    ${kcSanitize(messagesPerField.getFirstError('firstName','lastName','email'))?no_esc}
                </div>
            </#if>

            <input type="text" id="firstName" name="firstName" value="${(user.firstName!'')}" 
                   placeholder="First Name" required/>
            
            <input type="text" id="lastName" name="lastName" value="${(user.lastName!'')}" 
                   placeholder="Last Name" required/>
            
            <input type="email" id="email" name="email" value="${(user.email!'')}" 
                   placeholder="Email" required/>

            <button type="submit">Update</button>
        </form>
    </#if>
</@layout.registrationLayout>