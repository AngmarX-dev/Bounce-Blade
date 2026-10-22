#if UNITY_EDITOR
using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace BounceBlade.Editor
{
    [InitializeOnLoad]
    internal static class BounceBladeAutoSetup
    {
        static BounceBladeAutoSetup() { EditorApplication.delayCall += BounceBladeBuild.PrepareProject; }
    }

    public static class BounceBladeBuild
    {
        private const string MainScene = "Assets/Scenes/Main.unity";

        public static void PrepareProject()
        {
            Directory.CreateDirectory("Assets/Scenes");
            if (!File.Exists(MainScene))
            {
                Scene scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
                EditorSceneManager.SaveScene(scene, MainScene);
            }
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(MainScene, true) };
            PlayerSettings.companyName = "AngmarX-dev";
            PlayerSettings.productName = "Bounce Blade";
            PlayerSettings.bundleVersion = "2.0.0";
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
            PlayerSettings.runInBackground = true;
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, "com.angmarx.bounceblade.unity");
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel24;
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            Texture2D icon = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/Resources/BounceBlade/Images/App Icon & Logo/App Icon.png");
            if (icon != null) PlayerSettings.SetIconsForTargetGroup(BuildTargetGroup.Android, new[] { icon });
        }

        [MenuItem("Bounce Blade/Build Android APK")]
        public static void BuildAndroid()
        {
            PrepareProject();
            Directory.CreateDirectory("Build/Android");
            BuildPlayerOptions options = new BuildPlayerOptions
            {
                scenes = new[] { MainScene },
                locationPathName = "Build/Android/BounceBlade.apk",
                target = BuildTarget.Android,
                options = BuildOptions.None
            };
            BuildReport report = BuildPipeline.BuildPlayer(options);
            if (report.summary.result != BuildResult.Succeeded)
                throw new BuildFailedException("Bounce Blade Android build failed: " + report.summary.result);
            Debug.Log("Bounce Blade APK created at Build/Android/BounceBlade.apk");
        }
    }
}
#endif
