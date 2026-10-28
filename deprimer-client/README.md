# Deprimer Client

Deprimer Client is an independent Windows desktop launcher for Minecraft: Java Edition, currently in development. This page is the public application profile for its Minecraft Java Edition Game Services API access request.

This directory documents Deprimer Client. The other files in the parent repository belong to the existing larpclient project; this page does not claim that they contain the Deprimer Client Windows launcher source code.

## Application overview

- **Application:** Deprimer Client (DeprimerClient in the Microsoft application registration)
- **Platform:** Windows 10 / Windows 11, x64
- **Current development version:** 0.10.0
- **Game configuration:** Minecraft: Java Edition 1.21.11; Fabric by default, with a Vanilla option
- **Purpose:** manage separate local game instances, prepare required game files and Java, and launch Minecraft for an authenticated user with a valid Java Edition entitlement
- **Publisher contact:** use this repository's [issue tracker](https://github.com/remfromdc/larpclient/issues) for public project questions. Never post credentials or authentication tokens.

The launcher provides account, settings, instances and skin sections with a game launch screen. Microsoft/Minecraft sign-in is implemented, but a successful end-to-end account connection has not yet been verified. Minecraft Services access approval is being requested; this page does not imply approval or endorsement.

## Why Minecraft API access is requested

Deprimer Client needs access to the Minecraft authentication, entitlement and player profile services to exchange the user's Xbox identity for a Minecraft session, confirm access to Java Edition, and retrieve the player name and UUID needed for an authenticated launch.

The intended user flow is:

1. The user chooses to connect a Minecraft account in Deprimer Client.
2. The system browser opens Microsoft's sign-in and consent pages.
3. A public desktop OAuth client uses authorization code authentication with PKCE and a local callback.
4. The application follows the Microsoft, Xbox Live, XSTS and Minecraft Services authentication chain.
5. It checks the account's Java Edition entitlement and obtains the Minecraft profile before allowing an authenticated game launch.

If authentication, entitlement validation or Minecraft API access fails, the launcher reports the error. It does not bypass an API refusal or replace the user's identity with a borrowed application registration.

## Microsoft application registration

| Field | Value |
| --- | --- |
| Application name | DeprimerClient |
| Application (client) ID | `333209bc-cd8f-4885-9e95-c3dccda7db3a` |
| Account audience | Personal Microsoft accounts |
| Application type | Public Windows desktop client |
| Redirect URI | `http://localhost` |
| Authentication library | MSAL.NET |
| Client secret | None |

The client ID is a public application identifier, not a password or secret.

## Credentials and local data

Microsoft sign-in occurs on Microsoft's browser pages. Deprimer Client does not ask users to type their Microsoft password into the launcher.

The Microsoft token cache is protected locally with Windows DPAPI for the current Windows user. Minecraft access tokens are kept in memory. The launcher does not import account sessions or tokens from the official Minecraft Launcher.

Deprimer Client also has its own, separate launcher access system. That access does not grant ownership of Minecraft and does not replace the Microsoft account or Java Edition entitlement checks.

Game instances have separate local directories. Minecraft game files and the required Java runtime are downloaded from Mojang sources; Fabric components are obtained from Fabric sources. The public profile does not distribute Minecraft game files, account tokens or user data.

## Development and distribution status

The project is in development. Local preparation of the game files, Java runtime, Fabric and Vanilla configurations has been tested. The full Microsoft-to-Minecraft account sign-in still requires live verification and any required API approval. No public download is offered on this page.

A custom website and domain have not yet been registered or hosted. This GitHub page is the current public reference for the application and its API review request.

## Independence

Deprimer Client is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft. Minecraft names and trademarks belong to their respective owners.
