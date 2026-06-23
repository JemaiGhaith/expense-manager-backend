<#macro accountLayout title="Account Management">
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title} - Coral-io</title>
    
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css"/>
    
    <style>
        body {
            font-family: 'Montserrat', sans-serif;
            margin: 0;
            padding: 0;
            background: #f6f5f7;
            color: #333;
        }
        
        .account-header {
            background: linear-gradient(to right, #FF4B2B, #FF416C);
            padding: 20px 0;
            margin-bottom: 30px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        .header-container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 20px;
            text-align: center;
        }
        
        .account-header a {
            color: white;
            text-decoration: none;
        }
        
        .account-header h1 {
            margin: 0;
            font-size: 24px;
            font-weight: bold;
        }
        
        .account-nav {
            display: flex;
            justify-content: center;
            gap: 20px;
            margin-top: 15px;
        }
        
        .account-nav a {
            color: white;
            text-decoration: none;
            font-size: 14px;
            padding: 8px 16px;
            border-radius: 4px;
            transition: background 0.3s;
        }
        
        .account-nav a:hover {
            background: rgba(255, 255, 255, 0.1);
        }
        
        .main-container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 20px 40px;
        }
    </style>
</head>
<body>
    <header class="account-header">
        <div class="header-container">
            <a href="#">
                <h1>Coral-io Account</h1>
            </a>
            <nav class="account-nav">
                <a href="./">Home</a>
                <a href="./profile">Profile</a>
                <a href="./password">Password</a>
                <a href="./sessions">Sessions</a>
            </nav>
        </div>
    </header>
    
    <main class="main-container">
        <#nested>
    </main>
</body>
</html>
</#macro>