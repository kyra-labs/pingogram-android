# Additional permission under GNU GPL version 3 section 7

Pingogram is licensed under the GNU General Public License, version 3 or (at your option) any later version
([LICENSE](LICENSE)). In addition, as permitted by section 7 of the GNU GPL version 3, the copyright holders of
Pingogram grant the following additional permission:

If you modify Pingogram, or any covered work, by linking or combining it with the Google Play services and Firebase
client libraries for Android (the libraries published in the Maven groups `com.google.android.gms` and
`com.google.firebase`, and the libraries they depend on that are published under the same terms), or a modified version
of those libraries, containing parts covered by the terms of the Android Software Development Kit License or other
Google terms that apply to those libraries, the licensors of Pingogram grant you additional permission to convey the
resulting work.

This permission covers only those libraries. Everything else in a conveyed work, including Pingogram's own code,
remains under the GNU GPL version 3 or later, and its Corresponding Source must be provided as the GPL requires.

You may remove this additional permission from your copy of Pingogram, or from works based on it, as section 7 allows.

Why: the `gms` build of Pingogram receives notifications through Firebase Cloud Messaging, which needs these
proprietary libraries. The `foss` build contains none of them, and the build refuses any that reach it
(`docs/adr/0004-push-delivery.md`, `docs/adr/0010-gpl-additional-permission-for-play-services.md`).
