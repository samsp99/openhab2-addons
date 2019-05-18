# Mail Binding

The Mail binding provides support for sending emails from rules. 

## Supported Things

There are three things: `smtp`, `imap` and `pop3` which represents respective servers.

## Thing Configuration

### SMTP server (`smtp`)

There are two mandatory parameters `hostname` and `sender`.

The `hostname` may contain an IP address or a FQDN like `smtp.gmail.com`.
The `sender` must be a valid email address used as sender address for mails.

The `security`, `port`, `username` and `password` parameters are optional.

The `security` parameter defines the transport security and can be set to `PLAIN` (default), `SSL` or `TLS`.
The `port` parameter is used to change the default ports for the SMTP server. 
Default ports are `25` (for `PLAIN` and `TLS`) and `465` (for `SSL`).
For authentication, `username` and `password` can be supplied.
If one or both are empty, no authentication data is provided to the SMTP server during connect. 

### IMAP server (`imap`) / POP3 server (`pop3`) 

There is one mandatory parameter: `hostname`, `username`, `password`.
The `hostname` may contain an IP address or a FQDN like `mail.gmail.com`.
For authentication `username` and `password` need to be supplied.

The `refresh`, `security`, `port`, `username` and `password` parameters are optional.

The `refresh` parameter is the time in seconds between two refreshes of the thing`s channels.
If omitted, the default of 60s is used.
The `security` parameter defines the transport security and can be set to `PLAIN` (default), `SSL` or `TLS`.
The `port` parameter is used to change the default ports for the SMTP server. 
Default ports are `143` (for `PLAIN` and `TLS`) and `993` (for `SSL`) in the case of `imap` or `110` (for `PLAIN` and `TLS`) and `995` (for `SSL`) in the case of `pop3`.

## Channels

There are no channels for the `smtp` thing.
The `imap` and `pop3` things can be extended with `mailcount`-type channels.

### Type `mailcount`

Each channel has two parameters: `name` and `type`.
The `name` is mandatory and denotes the folder name on the given account.
The `type` parameter can be `UNREAD` or `TOTAL` (default).
Channels with type `UNREAD` give the number on unread mails in that folder. 


## Full Example

mail.things:

```
Thing mail:smtp:samplesmtp [ hostname="smtp.example.com", sender="foo@example.com", security="TLS", username="user", password="pass" ]

Thing mail:imap:sampleimap [ hostname="imap.example.com", security="SSL", username="user", password="pass" ] {
    Channels:
        Type mailcount : inbox_total [ folder="INBOX", type="TOTAL" ]
        Type mailcount : inbox_unread [ folder="INBOX", type="UNREAD" ]
}
```


mail.items:

```
Number InboxTotal  "INBOX [%d]"        { channel="mail:imap:sampleimap:inbox_total" }
Number InboxUnread "INBOX Unread [%d]" { channel="mail:imap:sampleimap:inbox_unread" }
```

mail.sitemap:

```
sitemap demo label="Main Menu"
{
    Frame {
        Text item=InboxTotal
        Text item=InboxUnread
    }
}
```

## Rule Action

This binding includes rule actions for sending mail.
Six different actions available:

* `sendMail(String recipient, String subject, String text)`
* `sendMail(String recipient, String subject, String text, String URL)`
* `sendMail(String recipient, String subject, String text, List<String> URL)`
* `sendHtmlMail(String recipient, String subject, String htmlContent)`
* `sendHtmlMail(String recipient, String subject, String htmlContent, String URL)`
* `sendHtmlMail(String recipient, String subject, String htmlContent, List<String> URL)`

The `sendMail(...)` send a plain text mail (with attachments if supplied).
The `sendHtmlMail(...)` send a HTML mail (with attachments if supplied).
 
Since there is a separate rule action instance for each `smtp` thing, this needs to be retrieved through `getActions(scope, thingUID)`.
The first parameter always has to be `mail` and the second is the full Thing UID of the SMTP server that should be used. 
Once this action instance is retrieved, you can invoke the action method on it.

Examples:

```
val mailActions = getActions("mail","mail:smtp:sampleserver")
mailActions.sendMail("recipient@foo.bar", "Test subject", "This is the mail content.")
```

```
import java.util.List

val List<String> attachmentUrlList = newArrayList(
  "http://some.web/site/snap.jpg&param=value",
  "file:///tmp/201601011031.jpg")
val mailActions = getActions("mail","mail:smtp:sampleserver")
mailActions.sendHtmlMail("recipient@foo.bar", "Test subject", "<h1>Header</h1>This is the mail content.", attachmentUrlList)
```

