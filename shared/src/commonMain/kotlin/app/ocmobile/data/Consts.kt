package app.ocmobile.data

/**
 * Guest paths inside the OpenCode environment. They live in commonMain because
 * [app.ocmobile.api.Session.displayTitle] needs [CHAT_DIR] and `Session` is
 * shared: on iOS these paths are names in the server's workspace, not paths on
 * the phone.
 */
const val CHAT_DIR = "/root/chats"
