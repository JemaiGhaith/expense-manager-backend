<form id="kc-register-form" action="${url.loginAction}" method="post">
  <div class="form-group">
    <label for="username">Nom d'utilisateur</label>
    <input id="username" name="username" type="text" value="${username!}" />
  </div>
  <div class="form-group">
    <label for="email">Email</label>
    <input id="email" name="email" type="email" value="${email!}" />
  </div>
  <div class="form-group">
    <label for="password">Mot de passe</label>
    <input id="password" name="password" type="password" />
  </div>
 <div class="form-group">
  <label for="role">Rôle</label>
  <select id="role" name="role">
    <option value="" disabled selected>Choisir un rôle</option>
    <option value="employe">Employé</option>
    <option value="manager">Manager</option>
  </select>
</div>

</form>
