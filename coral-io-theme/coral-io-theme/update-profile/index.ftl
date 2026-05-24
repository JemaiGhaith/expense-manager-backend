<#import "../login/template.ftl" as layout>

<@registrationLayout>
    <h1>Mettre à jour le profil</h1>
    <form action="${url.updateAction}" method="post">
        <div class="mb-3">
            <label for="firstName">Prénom</label>
            <input type="text" id="firstName" name="firstName" class="form-control" value="${user.firstName!}">
        </div>
        <div class="mb-3">
            <label for="lastName">Nom</label>
            <input type="text" id="lastName" name="lastName" class="form-control" value="${user.lastName!}">
        </div>
        <div class="mb-3">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" class="form-control" value="${user.email!}">
        </div>
        <button type="submit" class="btn btn-success">Mettre à jour</button>
    </form>
</@registrationLayout>
