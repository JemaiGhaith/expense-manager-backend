<#macro registrationLayout displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <title><#nested "header"></title>

   <#-- Inclure les styles -->
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    
 <link rel="stylesheet" href="resources/css/styles.css">
<link rel="stylesheet" href="resources/css/bootstrap.min.css">


    <!-- Scripts Keycloak -->
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>
<body class="login-pf">

<header id="header" class="header d-flex align-items-center fixed-top">
  <div class="header-container container-fluid container-xl position-relative d-flex align-items-center justify-content-between">
    <a href="#" class="logo d-flex align-items-center me-auto me-xl-0">
      <h1 class="sitename">Coral-io</h1>
    </a>
  </div>
</header>

<div class="login-container">
    <div class="login-content">
        <#if displayMessage && message?has_content>
            <div class="alert alert-${message.type}">
                <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <#nested "form">

        <#if displayInfo>
            <div id="kc-info">
                <#nested "info">
            </div>
        </#if>
    </div>
</div>
</body>
</html>
</#macro>
