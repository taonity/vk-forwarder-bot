## vk-forwarding-bot
A bot that forwards content from a VK group to a Telegram channel

### Features
- Forward post photos and videos
- Forward story videos
- Polling VK once per hour to forward new content
- HTTP endpoint to force forwarding cycle
- 1 DB row per each VK-Telegram group-channel pair

### How to run
The main challenge is to obtain a VK token. Besides that, the process is straightforward.

#### Obtain VK token
Follow the instructions:
- Create a VK app here https://id.vk.com/about/business/go
- If you have russian ID, then go to the Access tab and provide personal information
- If you don't have russian ID, then you have to email devsupport@corp.vk.com the following details:
  - ID of the account on the service https://id.vk.com/about/business/go
  - Information about the type of identity document: passport/ID card/other.
  - Country of issue of the document
  - Information about the number of the identity document: series, number, and other data
  - Phone number, which, if necessary, can be used for communication for data verification

  A reply should arrive within several working days
- In the Access tab toggle on Wall, Communities, Stories, and Videos advanced access (some accesses might be redundant).
- Go to the App tab and fill the `Base domain` field with `localhost`
- Fill `Trusted redirect URL` with `https://localhost`
- Generate a token. Registering a separate VK account and generating the token by its name is a good idea.
  - Send the request via browser to get `code`:
      ```
      https://oauth.vk.com/authorize?client_id=<App ID>&display=page&redirect_uri=https://localhost&scope=offline,photos,video,wall,groups,stories,status&response_type=code&v=5.199
      ```
    Some scopes might be redundant.
  - Send the request via browser to get a `token`:
    ```
    https://oauth.vk.com/access_token?client_id=<App ID>&client_secret=<Secure key>&redirect_uri=https://localhost&code=<code>
    ```
#### Obtain Telegram token
Find https://t.me/BotFather Telegram bot, send `/newbot` command, and follow the instructions.

#### Build
The best way to run the app is using IntelliJ. Clone the repo and open it with IntelliJ. `Run/Debug configurations` will be used to run the app. Create a Maven configuration with the following details in the fields:
- Run - `clean package -DskipTests=true`
- Profiles - `automation docker`
- Modify options -> Select Add VM options
- VM options:
  ```
  -DFORWARDER_VK_TOKEN=<VK token>
  -DFORWARDER_VK_USER_ID=<VK user id>
  -DFORWARDER_VK_USERNAME=<VK username>
  -DFORWARDER_VK_PASSWORD=<VK password>
  -DFORWARDER_TG_TOKEN=<TG token>
  -DFORWARDER_TG_BOT_USERNAME=<TG bot username>
  ```
Make sure Docker is up and press the Run button to build the project.

#### Run
Run `docker compose -f .\target\docker\test\docker-compose.yml up -d` to start the app.

#### Add VK-Telegram group-channel pair
Use a DB GUI to access Postgres. Use the credentials from this [env file](src/templates/docker/.test-env).
Add a row into the `vk_group_details` table:
```
INSERT INTO vk_group_details (vk_group_id,tg_channel_id)
VALUES (<VK group id>,'<TG channel id>');
```
The table column's description

| id               | vk_group_id                                                           | last_forwarded_post_date_time                    | last_forwarded_story_date_time                     | tg_channel_id                          |
|------------------|-----------------------------------------------------------------------|--------------------------------------------------|----------------------------------------------------|----------------------------------------|
| Optional         | Required                                                              | Optional                                         | Optional                                           | Required                               |
| Autogenerated id | See this [FAX](https://vk.com/faq18062?lang=en) to know how to get it | By default is taken as current time minus 1 hour | By default is taken as current time minus 24 hours | Use https://t.me/myidbot to get the id |

#### Start forwarding
To start forwarding you can either restart the app container or send the HTTP request `GET http://localhost:9015/forward` with the `Authorization` header `1234`.

### Deploy
The app can be deployed manually or using [generaltao725/docker-webhook](https://github.com/taonity/docker-webhook) tool. 
