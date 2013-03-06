# noir-auth-app

A complete authentication web app based on Clojure/ClojureScript, Compojure, lib-noir, Enlive and MongoDB.

It's meant to be used as a base app for building Clojure web apps that require authentication.

It features...

+ signups with email verification
+ logins by username or email
+ password resets
+ handling of email, username and password changes
+ account deletions
+ a minimal, responsive web UI
+ an admin web UI (also minimal and responsive :)

See the [URL Tour](#url-tour) for a more detailed look at the functionality.

Besides [Compojure](https://github.com/weavejester/compojure) and [lib-noir](https://github.com/noir-clojure/lib-noir), noir-auth-app also uses [Enlive](https://github.com/cgrand/enlive) for templating, [CongoMongo](https://github.com/aboekhoff/congomongo) to interact with the database, [Postal](https://github.com/drewr/postal) to send emails, and [shoreleave-remote](https://github.com/shoreleave/shoreleave-remote) (together with [shoreleave-remote-ring](https://github.com/shoreleave/shoreleave-remote-ring)) to call remote functions on the server from ClojureScript.

You can see the app running at http://noir-auth-app.herokuapp.com (in this demo all users are given admin privileges, so that you can see the admin UI, but account deletions from this UI are disabled).


## URL Tour


### Home

#### `GET /`

If user is not logged in, it displays a generic welcome message and a login link.

If user is logged in, it displays a personalized greeting and links to settings and logout.

If the [flash](http://yogthos.github.com/lib-noir/noir.session.html#var-flash-get) contains a value for the key `:notice`, it's also displayed.


### Signup

#### `GET /signup`

Displays the signup form.

#### `POST /signup`

This HTTP POST is used to submit the signup form to the server. The server validates the signup request. If it's ok, it sends an activation code to the email address specified in the signup, and it redirects to the login page. If validations fail, it returns the signup form with the errors. If the error is that there's already an account with the submitted email, but it has not been activated yet, then a link to resend the activation code is provided.

All validations are done at the app level, and some of them also at the database level (ex. database indexes are used to ensure uniqueness of usernames, emails and activation codes).

#### `GET /activate/:activation-code`

The email sent when signing up contains a link like this. When following it, the activation code is looked up in the database and, if found and has not expired, the corresponding account is activated and the user is automatically logged in and redirected to `/`.

If the activation code is not found or has expired an appropriate message is displayed. The message about the expired activation code contains a link to ask for a new one (`/resend-activation?email=:email`).

#### `POST /resend-activation?email=:email`

Looks up the email in the database and if corresponds to a not yet activated account, it resets its activation code and sends it to that email address. Finally, it redirects to `/login`.

If the email is not found or it corresponds to an already activated account, an appropriate message is displayed.

The reason to use POST instead of GET is the same as for `/logout` (see below).


### Login/Logout

#### `GET /login`

Shows the login form if not logged in, otherwise redirects to `/`.

#### `POST /login`

Logs in the user if credentials are correct. It's possible to log in by username and password, or email and password. If the session object contains a :return-to key, removes it and redirects to the URL stored in that key, otherwise redirects to `/`. This works in conjunction with the `ensure-logged-in` macro, which is called before serving any page that requires authentication, to redirect not logged in users to the login form and then, once logged in, back to the originally requested page.

When logging in, the user id is stored in the session object. If the account is an admin account, an :admin entry with the value of true is also stored in the session. Which account is an admin account is decided in the `save-user-info-in-session` function (by default, the admin account is the account with the "admin" username).

If credentials are incorrect, an appropriate message is displayed. If the username, or email, corresponds to an account that has not been activated, then a link to resend the activation code (`/resend-activation?email=:email`) is provided.

#### `POST /logout`

Clears the session object (which contains the id of the logged in account and a key indicating that it's an admin account if that's the case) to log out the account and redirects to `/`.

The reason why logouts are handled through HTTP POST instead of GET is to avoid that someone could log out a user by having him load a page containing an image tag like `<img src="http://example.com/logout" />`.


### Settings

#### `GET /settings`

Shows the settings form if the user is logged in, otherwise redirects to `/login`.

#### `POST /username-changes`

Used from the `/settings` page to change the username of the logged in account.

If the user is logged in, the submitted username is checked for length, valid characters and uniqueness (see the `valid?` function). Uniqueness is ensured by checking it at the application level and enforcing it at the database level. The username case is preserved, but the uniqueness check is case insensitive.

The user is then redirected to `/settings` and, if there are errors, appropriate messages are shown.

If the user is not logged in, it redirects to `/login` (see `ensure-logged-in` in the source code).

#### `POST /email-changes`

Used from the `/settings` page to request an email change. Appropriate messages are displayed if the email is not valid, or it's already taken, or it's taken but not confirmed. Validations are done in `noir-auth-app.models.user/valid?`.

A link ( `/email-changes/:email-change-code/verify` ) is sent to the requested new email address for the user to confirm it.


#### `POST /email-changes/cancel`

It allows to cancel an email change request. This link is available in the `/settings` page while there's an email change waiting to be confirmed.

The reason to use POST instead of GET is the same as for `/logout` (see above).


#### `POST /email-changes/resend-confirmation`

It resends the link to confirm the new address of an email change request. This link is available in the `/settings` page while there's an email change waiting to be confirmed.

The reason to use POST instead of GET is the same as for `/logout` (see above).


#### `GET /email-changes/:email-change-code/verify`

When a user changes his email address in `/settings`, a link like this is sent in an email to the new address for the user to confirm it.

When following the link, the user is redirected to `/settings and a message is displayed telling her that the email change is now effective, or that there were errors (ex. the email may have been taken by someone else since the change request was made). If the user is not logged in when following the link, he will be first redirected to the login page.


#### `POST /password-changes`

Used from the `/settings` page to change the password of the logged in account. If not logged in, redirects to `/login`.

The password is checked for length, then the user is redirected to `/settings` and, if there are errors, appropriate messages are shown.


<h4><code>POST /_fetch</code><br>
<code>remote=delete-account&params=nil</code></h4>

This is handled by `(defremote delete-account ...)`. It deletes the logged in user and clears the session.

When following the "delete account" link in the Settings page, ClojureScript code shows an alert asking for confirmation, and if the user confirms it, `remote-callback` (from Chris Granger's [fetch library](https://github.com/ibdknox/fetch)) is used to call the `delete-account` function in the server (internally, `remote-callback` makes the HTTP POST to run the remote function).


### Password resets

#### `GET /password-resets`

This is the URL of the "forgot password?" link. Shows a form asking for the email address of the account whose password has to be reset.

#### `POST /password-resets`

The form to ask for a password reset submits the email address through this POST. This looks up the address in the database and, if found, it generates a password reset code, stores it, and sends it to that address. Finally, it redirects to `/login`.

Notice that it's possible to request a password reset for a not yet activated account (and when the password reset is completed the account will be activated too). For details, see comments in the source code of the `change-password-with-reset-code!` function.

The uniqueness of the generated password reset code is ensured by checking it at the application level and enforcing it at the database level. Because of how the code is generated it's very improbable that it's not unique, but if that happens, the user will be asked to try it again.

If the address is not found or there are any errors (such as, mainly, a generated reset code that is not unique), the form to ask for a password reset is shown again, but this time with a message explaining the problem.

#### `GET /password-resets/:reset-code/edit`

A link like this is sent to the email address of an account for which a password reset has been requested. It shows a form allowing the user to reset the account's password.

#### `PUT /password-resets`

The form to reset a password using a reset code (`/password-resets/:reset-code/edit`) is submitted with a PUT request like this.

If the reset code is found and has not expired, and the new password is valid, then the password is reset and the user is redirected to `/login`. If, instead, there are errors, then the form to reset the password is rendered with the corresponding error messages.


### Admin

#### `GET /admin`

It shows the admin interface if the logged in account is an admin account, otherwise redirects to `/login`. (Logged in admin accounts can be distinguished by having a "truthy" value in the session's :admin key, see `POST /login`.)

The admin interface shows a paged list of all users ordered by creation date from the most recent to the oldest. The list is paged by date instead of page number. This means that it can be browsed by date using the URL-based interface provided by this pagination. For example, to get a paged list with the users created before a given datetime, just specify the datetime (in [ISO 8601](http://en.wikipedia.org/wiki/ISO_8601)) with an `until` parameter in the URL, like this: `/admin?until=2012-08-05T19:29Z`. If specifying a positive time offset from UTC, remember to URL encode the "+" (`%2B`), otherwise it will be interpreted as a space (ex. `/admin?until=2012-08-05T19:29%2B02:00`).


<h4><code>POST /_fetch</code><br>
<code>remote=delete-user&params=[:user-id]</code></h4>

An example of data sent through this HTTP POST

    remote=delete-user
    params=["502d27bb0364a9dbc4c67dfd"]

which, once URL encoded, becomes

    remote=delete-user&params=%5B%22502d27bb0364a9dbc4c67dfd%22%5D

This is handled by `(defremote delete-user [user-id] ...)`. If the logged in user is an admin, it deletes the user specified in the POST data, and it returns 1 if successful, 0 otherwise.

From `/admin`, when following the "Delete" link for a user, the ClojureScript code in main.cljs, after asking for confirmation, uses [shoreleave-remote](https://github.com/shoreleave/shoreleave-remote)'s `remote-callback` to call `delete-user` on the server (internally, shoreleave-remote makes the HTTP POST).


## Install

Clone the repository from GitHub using git:

    git clone git://github.com/xavi/noir-auth-app.git

or use the ZIP button above to download the code as a zip file.

To run it locally you need [MongoDB](http://www.mongodb.org/), [Clojure](http://clojure.org/), [Leiningen](https://github.com/technomancy/leiningen) and [Foreman](https://github.com/ddollar/foreman) (the latter not strictly necessary but recommended).

First, start MongoDB.

Create a file named `.env` in the app's root directory to store all the required configuration variables, which will be automatically read into environment variables when the app is started with Foreman (noir-auth-app follows the [twelve-factor methodology](http://www.12factor.net/)), and finally read by the app code. This is an example of the `.env` file with all the required configuration vars:

    MONGODB_URI=mongodb://localhost/example-db-name
    SMTP_SERVER=smtp.example.com
    SMTP_USERNAME=admin@example.com
    SMTP_PASSWORD=example-password
    EMAILS_FROM="Example <admin@example.com>"
    CONTACT_EMAIL=hello@example.com

Now the app can be started with

```bash
foreman start
```


## License

Copyright (C) 2012–2013 Xavi Caballé

Distributed under the Eclipse Public License, the same as Clojure.

