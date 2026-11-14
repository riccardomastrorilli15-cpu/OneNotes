# Transitional AndroidX Activity bridge

`io.github.oneuiproject.sesl:core:1.3.0` replaces AndroidX Core but predates
interfaces and menu helper classes required by `androidx.activity:activity:1.5.1`
(the minimum required by the Hilt version used here). This module includes only
the ten missing AndroidX Core classes needed for that Activity version; it does
not contain or replace the SESL core itself. They are unmodified AndroidX source,
under Apache-2.0, copyright The Android Open Source Project:

- upstream: <https://github.com/androidx/androidx/tree/d35ed925785d1aa3aa2f66827a940c49dac4495c/core/core/src/main/java/androidx/core>
- upstream snapshot: `d35ed925785d1aa3aa2f66827a940c49dac4495c` (2022-05-31)
- license text: [`../licenses/Apache-2.0.txt`](../licenses/Apache-2.0.txt)

`app` pins Activity to 1.5.1 strictly to prevent an unnoticed upgrade expecting
yet more APIs; the app excludes the official AndroidX core AAR to avoid duplicate
classes. This is a compatibility workaround, **not** proof that all combinations
of the old SESL fork, Activity, Hilt and newer Android versions are safe.
Integration, lifecycle and release tests remain necessary. Remove this bridge
when migrating the complete OneUI stack to a mutually compatible, audited fork;
never ship it together with an AndroidX Core containing these classes.
