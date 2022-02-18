using UnityEditor;
using System.Linq;
using System;
using System.IO;

class Builder
{
    // Setup keystore, alices, password for Andorid
    // keystore pass
    private const string MY_KEYSTORE_PASS = "test-debug";
    // Key  alias pass
    private const string MY_KEY_ALIAS_PASS = "test-debug";
    // Key alias name
    private const string MY_KEY_ALIAS_NAME = "tets-debug";
    // keystore
    // ex: key.keystore
    private const string MY_KEYSTORE = "test-key.keystore";
    // app name
    private const string MY_APP_NAME = "test-app";


    static void BuildIL2CPPAndroid()
    {
        ProcessAndroidKeystore();

        BuildPlayerOptions buildPlayerOptions = new BuildPlayerOptions();

        // app name
        buildPlayerOptions.locationPathName = MY_APP_NAME + ".apk";

        // target android
        buildPlayerOptions.target  = BuildTarget.Android;
        buildPlayerOptions.options = BuildOptions.None;

        // setup enabled scenes
        buildPlayerOptions.scenes  = GetEnabledScenes();

        // SetScriptingBackend for IL2CPP
        PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.IL2CPP);

        // Support ARMv7 and ARMv64
        AndroidArchitecture aac = AndroidArchitecture.ARM64;
        aac |= AndroidArchitecture.ARMv7;
        PlayerSettings.Android.targetArchitectures = aac;

        // Use Apk extension file to reduce apk size
        // https://support.google.com/googleplay/android-developer/answer/2481797?hl=en#zippy=%2Cadd-or-change-expansion-files
        PlayerSettings.Android.useAPKExpansionFiles = true;

        var buildReport = BuildPipeline.BuildPlayer(buildPlayerOptions);

       if (buildReport.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded)
            throw new Exception($"Failed to build Android with {buildReport.summary.result} status");

        var summary = buildReport.summary;
        Console.WriteLine("Succeed to create " + buildPlayerOptions.locationPathName + " size: "+ summary.totalSize);

    }

    static void BuildMonoAndroid()
    {
        ProcessAndroidKeystore();

        BuildPlayerOptions buildPlayerOptions = new BuildPlayerOptions();

        // app name
        buildPlayerOptions.locationPathName = MY_APP_NAME + ".apk";

        // target android
        buildPlayerOptions.target = BuildTarget.Android;
        buildPlayerOptions.options = BuildOptions.None;

        // setup enabled scenes
        buildPlayerOptions.scenes = GetEnabledScenes();

        // SetScriptingBackend for Mono
        PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.Mono2x);

        // Mono mode only supports ARMv7, but not ARMv64
        AndroidArchitecture aac = AndroidArchitecture.ARMv7;
        PlayerSettings.Android.targetArchitectures = aac;

        var buildReport = BuildPipeline.BuildPlayer(buildPlayerOptions);

        if (buildReport.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded)
            throw new Exception($"Failed to build Android with {buildReport.summary.result} status");

        var summary = buildReport.summary;
        Console.WriteLine("Succeed to create " + buildPlayerOptions.locationPathName + " outputPath: " + summary.outputPath);

    }


    static void BuildIOS()
    {
        BuildPlayerOptions buildPlayerOptions = new BuildPlayerOptions();

        // app name
        buildPlayerOptions.locationPathName = MY_APP_NAME;

        // target iOS
        buildPlayerOptions.target = BuildTarget.iOS;
        buildPlayerOptions.options = BuildOptions.None;

        // setup enabled scenes
        buildPlayerOptions.scenes = GetEnabledScenes();

        var buildReport = BuildPipeline.BuildPlayer(buildPlayerOptions);

        if (buildReport.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded)
            throw new Exception($"Failed to build IOS with {buildReport.summary.result} status");

        var summary = buildReport.summary;
        Console.WriteLine("Succeed to create " + buildPlayerOptions.locationPathName + " outputPath: " + summary.outputPath);
    }

    static string[] GetEnabledScenes()
    {
        return (
            from scene in EditorBuildSettings.scenes
            where scene.enabled
            where !string.IsNullOrEmpty(scene.path)
            select scene.path
        ).ToArray();
    }

    static bool GetEnv(string key, out string value)
    {
        value = Environment.GetEnvironmentVariable(key);
        return !string.IsNullOrEmpty(value);
    }


    private static void ProcessAndroidKeystore()
    {
#if UNITY_2019_1_OR_NEWER
        PlayerSettings.Android.useCustomKeystore = false;
#endif

        if (!File.Exists(MY_KEYSTORE))
        {
            Console.WriteLine($":: {MY_KEYSTORE} not found, skipping setup, using Unity's default keystore");
            return;
        }

        PlayerSettings.Android.keystoreName = MY_KEYSTORE;

        string keystorePass;
        string keystoreAliasPass;

        if (GetEnv(MY_KEY_ALIAS_NAME, out string keyaliasName))
        {
            PlayerSettings.Android.keyaliasName = keyaliasName;
            Console.WriteLine($":: using ${MY_KEY_ALIAS_NAME} env var on PlayerSettings");
        }
        else
        {
            Console.WriteLine($":: ${MY_KEY_ALIAS_NAME} env var not set, using Project's PlayerSettings");
        }

        if (!GetEnv(MY_KEYSTORE_PASS, out keystorePass))
        {
            Console.WriteLine($":: ${MY_KEYSTORE_PASS} env var not set, skipping setup, using Unity's default keystore");
            return;
        }

        if (!GetEnv(MY_KEY_ALIAS_PASS, out keystoreAliasPass))
        {
            Console.WriteLine($":: ${MY_KEY_ALIAS_PASS} env var not set, skipping setup, using Unity's default keystore");
            return;
        }
#if UNITY_2019_1_OR_NEWER
        PlayerSettings.Android.useCustomKeystore = true;
#endif
        PlayerSettings.Android.keystorePass = keystorePass;
        PlayerSettings.Android.keyaliasPass = keystoreAliasPass;
    }
}
