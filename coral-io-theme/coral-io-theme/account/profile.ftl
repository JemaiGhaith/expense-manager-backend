<#ftl strip_whitespace=true>
<#import "account.ftl" as account>
<@account.accountLayout title="Edit Profile">
    
    <div class="profile-edit">
        <h2>Edit Profile</h2>
        
        <#if message??>
            <div class="alert alert-${message.type}">
                ${message.summary}
            </div>
        </#if>
        
        <form action="${url.accountUrl}" method="post" id="profile-form">
            <input type="hidden" id="stateChecker" name="stateChecker" value="${stateChecker}">
            
            <div class="form-group">
                <label for="username">Username</label>
                <input type="text" class="form-control" id="username" name="username" 
                       value="${(user.username)!''}" readonly>
            </div>
            
            <div class="form-group">
                <label for="email">Email</label>
                <input type="email" class="form-control" id="email" name="email" 
                       value="${(user.email)!''}" required>
            </div>
            
            <div class="form-group">
                <label for="firstName">First Name</label>
                <input type="text" class="form-control" id="firstName" name="firstName" 
                       value="${(user.firstName)!''}">
            </div>
            
            <div class="form-group">
                <label for="lastName">Last Name</label>
                <input type="text" class="form-control" id="lastName" name="lastName" 
                       value="${(user.lastName)!''}">
            </div>
            
            <div class="form-actions">
                <button type="submit" class="btn btn-primary">Save Changes</button>
                <a href="${url.accountUrl}" class="btn btn-default">Cancel</a>
            </div>
        </form>
    </div>
    
    <style>
        .profile-edit {
            max-width: 600px;
            margin: 0 auto;
            padding: 30px;
            background: white;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        .profile-edit h2 {
            margin-bottom: 30px;
            color: #333;
            text-align: center;
        }
        
        .form-group {
            margin-bottom: 20px;
        }
        
        .form-group label {
            display: block;
            margin-bottom: 5px;
            font-weight: 500;
            color: #555;
        }
        
        .form-control {
            width: 100%;
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 5px;
            font-size: 16px;
        }
        
        .form-control:read-only {
            background-color: #f5f5f5;
            cursor: not-allowed;
        }
        
        .form-actions {
            display: flex;
            gap: 10px;
            justify-content: center;
            margin-top: 30px;
        }
        
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 5px;
            font-size: 16px;
            cursor: pointer;
            text-decoration: none;
            display: inline-block;
        }
        
        .btn-primary {
            background: #FF4B2B;
            color: white;
        }
        
        .btn-default {
            background: #f0f0f0;
            color: #333;
        }
        
        .alert {
            padding: 15px;
            border-radius: 5px;
            margin-bottom: 20px;
        }
        
        .alert-success {
            background: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        
        .alert-error {
            background: #f8d7da;
            color: #721c24;
            border: 1px solid #f5c6cb;
        }
    </style>
</@account.accountLayout>