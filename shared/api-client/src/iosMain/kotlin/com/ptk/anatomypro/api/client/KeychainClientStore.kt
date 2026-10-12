package com.ptk.anatomypro.api.client

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Values in the Keychain, one generic password each (backend spec §5.1).
 *
 * Readable after the first unlock, so a refresh in the background does not find the token
 * missing; never synchronised to other devices — a second device signs in instead (§5.3).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainClientStore(private val service: String = "com.ptk.anatomypro") : ClientStore {

    /** Runs [block] with a query naming [key], releasing everything retained for it. */
    private fun <T> withQuery(key: String, extra: Map<CFTypeRef?, CFTypeRef?> = emptyMap(), block: (CFMutableDictionaryRef?) -> T): T {
        val retained = mutableListOf<CFTypeRef?>()
        fun bridged(value: Any): CFTypeRef? = CFBridgingRetain(value).also { retained += it }
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, bridged(service))
            CFDictionaryAddValue(query, kSecAttrAccount, bridged(key))
            for ((name, value) in extra) CFDictionaryAddValue(query, name, value)
            return block(query)
        } finally {
            retained.forEach { CFRelease(it) }
            CFRelease(query)
        }
    }

    override suspend fun read(key: String): String? =
        withQuery(key, mapOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)) { query ->
            memScoped {
                val found = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, found.ptr) != errSecSuccess) return@memScoped null
                val data = CFBridgingRelease(found.value) as? NSData ?: return@memScoped null
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            }
        }

    override suspend fun write(key: String, value: String) {
        remove(key)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val retained = CFBridgingRetain(data)
        try {
            withQuery(key, mapOf(kSecValueData to retained, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock)) { query ->
                val status = SecItemAdd(query, null)
                check(status == errSecSuccess) { "the Keychain refused to keep '$key' ($status)" }
            }
        } finally {
            CFRelease(retained)
        }
    }

    override suspend fun remove(key: String) {
        withQuery(key) { query -> SecItemDelete(query) }
    }
}
