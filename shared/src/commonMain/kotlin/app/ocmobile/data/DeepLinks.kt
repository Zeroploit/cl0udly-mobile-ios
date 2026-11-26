package app.ocmobile.data

/**
 * Entry point for the `cl0udly://` links iOS hands to the app; Swift calls it from `onOpenURL`.
 * True when the link is a connect code, which leaves the confirmation waiting in `ConnectLink.pending`.
 */
fun handleOpenUrl(url: String): Boolean = ConnectLink.offer(url)
