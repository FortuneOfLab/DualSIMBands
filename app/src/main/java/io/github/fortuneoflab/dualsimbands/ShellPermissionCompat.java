package io.github.fortuneoflab.dualsimbands;

import static io.github.fortuneoflab.dualsimbands.Tr.t;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** Resolves platform variants without treating a missing cleanup API as success. */
final class ShellPermissionCompat {
    interface ReleaseVerifier { void verify() throws Exception; }

    static String release(Object manager, Class<?> api, int uid, ReleaseVerifier verifier) throws Exception {
        List<Exception> failures = new ArrayList<>();
        // Some platform builds expose a UID-taking variant; older builds use no arguments.
        try {
            api.getMethod("stopDelegateShellPermissionIdentity", int.class).invoke(manager, uid);
            verifier.verify();
            return t("UID指定", "with UID");
        } catch (Exception e) { failures.add(unwrap(e)); }
        try {
            api.getMethod("stopDelegateShellPermissionIdentity").invoke(manager);
            verifier.verify();
            return t("従来方式", "legacy");
        } catch (Exception e) { failures.add(unwrap(e)); }
        try {
            // An empty list delegates NO permissions. Never use null: it delegates ALL.
            // Instrumentation.finish() subsequently removes the empty delegate record.
            api.getMethod("startDelegateShellPermissionIdentity", int.class, String[].class)
                    .invoke(manager, uid, new String[0]);
            verifier.verify();
            return t("互換方式", "compatible");
        } catch (Exception e) { failures.add(unwrap(e)); }
        IllegalStateException failure = new IllegalStateException(t("一時権限を手放したことを確認できませんでした", "Could not confirm that the temporary permissions were released"));
        for (Exception e : failures) failure.addSuppressed(e);
        throw failure;
    }

    private static Exception unwrap(Exception e) {
        if (e instanceof InvocationTargetException && e.getCause() instanceof Exception)
            return (Exception) e.getCause();
        return e;
    }
}
