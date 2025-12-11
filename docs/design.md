# Project Concept and Design

This document describes the concept and design of the project, including its architecture, key components/features, and how they interact with each other.

> [!WARNING]
> Some of the design of this software are **NOT** designed with full security in mind, so this software should **NEVER** be used in production and real-life cases.

## Project Overview

Callie is a relatively basic chat app created as a toy project for learning purposes. It is not intended to be a production-ready application, but rather a platform for exploring various technologies and concepts in web development, real-time communication, database management, and more.

This project is **never meant to be used in production** and is not designed with security, scalability, or performance in mind. It is a learning tool and should be treated as such.

The project has two parts, a server-side application (this repository) and a client-side application. Each of them has a different tech stack, except that standard network protocols (HTTP, WebSocket) are used on both sides to implement communication features between them.

_TODO_

## Architecture

_TODO_

## Key Components & Features

### 1. Users & Authentication

> [!NOTE]
> Note that this sections **is not** for authentication of the web management/monitoring interface, but for the user who will be using the client application, connected to the server, and sending messages to other users.

In the Callie system, a user has the following properties, stores across the database and memory:

- **Username**: A unique identifier for the user.
- **Password**: An [Argon2 hashed](https://github.com/bcgit/bc-java) password for authentication.
- A list of **Client Tokens**: Each of these tokens is associated with a client, and is used to validate authenticated clients so users doesn't have to re-login. This token doesn't expire unless the user choose to invalidate it.
- A list of **Session Tokens**: Each of these tokens is associated with a live (online) client session, and is used to identify connected WebSocket sessions. This token expires *every time the WebSocket connection is closed*.

The client authentication feature is implemented using a simple username/password-token system. To connect to the WebSocket handling the chat messages, the client must provide a one-time session token, and the WebSocket interface will always expect a token to be sent by the client as the first message (in a special format specified below), before the WebSocket begin to attempt to read from or write to the connection. If the token is not provided in a timeout period (currently 1 minute), the WebSocket connection will be closed by the server.

The session token generation and user login process follows the steps described below:

1. The client application sends an  `application/json` POST request to the `/api/v1/auth/login` endpoint.

    This endpoint **always** takes a [Machine ID](https://crates.io/crates/mid) (or any other ID that identifies a machine uniquely) and a `authType` string indicating what other info are needed for authentication, which should be either `"PASSWD"` or `"TOKEN"` (case-sensitive).

    When there is no stored client token, the `authType` is `"PASSWD"`, and the client sends a `username` and a `password` (in plaintext) with the above mentioned information.

    When there is a stored client token, the `authType` is `TOKEN`, and the client will provide a `username` and a `clientToken` (the stored client token in plaintext) with the above mentioned information.

2. The server checks the credentials (password and token) against the database.

    If the `authType` is `PASSWD`, the server just checks the username-password pair store in the database. The authentication is successful if they match, and is failed otherwise.

    If the `authType` is `TOKEN`, the server first check if the client token is matched with the provided username, and then a new client token is generated again from the exact same information (username, hashed password, and Machine ID) and compared with the provided client token. The authentication is successful if they match, and is failed otherwise.

3. If the credentials are valid, the server generates a session token and returns it in the response (with a client token, if the user was password authenticated, the client token should be generated from some combination of username, hashed password and Machine ID, or, if the user was client token authenticated, the client token is returned as-is). Otherwise, the server *should* always go back to step no.1 and require a password authentication from that MachineID/IP.
4. The client application stores the session token and uses it to authenticate subsequent WebSocket connections.

### 2. Real-time Messaging

The core feature of Callie is real-time messaging between users. This is implemented using WebSockets, allowing for low-latency, bidirectional communication between the client and server.

When a client connects to the WebSocket endpoint, it must first authenticate using the session token obtained during the login process. Once authenticated, the client can send and receive messages in real-time.

In the Callie system, messages that are transmitted between clients and/or server is structured in a JSON format, with the following fields:

- `token`: The session token of the client sending the message. This is used to identify the sender.
- `sender`: The username of the sender.
- `receiver`: The username of the receiver. This can be a specific user or a special value (e.g., `"SERVER"`) indicating that the message is intended for the server.
- `type`: The type of the message. This can be one of the following:
  - `MSG`: A standard chat message between users.
    - Markdown is supported in the message content, including code blocks, links, images, etc. Each client is responsible for rendering the markdown content appropriately.
    - The server should perform basic sanitization on the markdown content to prevent XSS attacks, but clients should also implement their own sanitization as needed.
    - If some images and files are embedded in the markdown content and it not accessible via public URLs yet, another mechanism should be used to upload these files/images to the server first, and then the server will provide public URLs for these resources, which can then be embedded in the markdown content.
  - `SYSTEM`: A system message (e.g., notifications, alerts).
  - `COMMAND`: A command message (e.g., user commands managing their account or settings).
  - `ERROR`: An error message (e.g., invalid commands, authentication errors).
  - `PING`: A ping message to check the connection status.
  - `PONG`: A pong message in response to a ping message.
  - Note that a client is NEVER supposed to send `SYSTEM` or `ERROR` messages; these types are reserved for server-to-client messages only.
- `content`: The content of the message. The details are described in the sections below according to the message type.
- `timestamp`: The timestamp when the message was sent, in ISO 8601 format (`YYYY-MM-DDTHH:mm.ss.sssZ`).
  - All timestamps must be in UTC.
  - Note that this timestamp must be accurate to the millisecond level, as it is used for ordering messages.
  - The client is responsible for generating this timestamp when sending a message.
  - The server will validate the timestamp and may reject messages with invalid timestamps using the following rules:
    - The timestamp must not be more than 5 minutes in the future compared to the server's current time.
    - The timestamp must not be more than 1 hour in the past compared to the server's current time.
    - If the timestamp is invalid, the server will respond with an `ERROR` message indicating the reason for rejection.