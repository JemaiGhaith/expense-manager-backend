<#ftl strip_whitespace=true>
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "form">
        <div id="kc-error-message">
            <p class="instruction">
                <#if message?? && message.summary??>
                    ${message.summary?no_esc}
                <#else>
                    An error occurred. Please try again later.
                </#if>
            </p>
            <#if client?? && client.baseUrl?has_content>
                <p><a id="backToApplication" href="${client.baseUrl}">Back to Application</a></p>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>