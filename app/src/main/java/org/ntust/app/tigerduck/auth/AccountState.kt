package org.ntust.app.tigerduck.auth

/**
 * Whether the school account is signed in, and how many times that has changed since the process
 * started. A watcher that sees it late can tell a sign-out and a sign-in in between, two changes
 * to the same value, from nothing having happened.
 */
data class AccountState(val signedIn: Boolean, val changes: Long)
