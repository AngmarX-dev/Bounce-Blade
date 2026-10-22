using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

public static class BounceBladeBootstrap
{
    [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
    private static void EnsureGame()
    {
        if (UnityEngine.Object.FindObjectOfType<BounceBladeGame>() == null)
            new GameObject("Bounce Blade Runtime").AddComponent<BounceBladeGame>();
    }
}

public sealed class BounceBladeGame : MonoBehaviour
{
    private enum Page { Menu, Stages, Shop, Settings, Help, Story, Game, Pause, Win, Lose }
    private enum Kind { Drone, Turret, Slime, Boss, Crate }
    private enum HazardKind { Spikes, Laser, Mover, Sticky, Portal, Switch, Power }
    private static readonly string[] Worlds = { "Neon Grid", "Crimson Circuit", "Azure Vault", "Magenta Maze", "Golden Core", "Obsidian Rift", "Solar Flare", "Quantum Abyss", "Storm Citadel", "Celestial Forge" };
    private static readonly string[] Slugs = { "NeonGrid", "CrimsonCircuit", "AzureVault", "MagentaMaze", "GoldenCore", "ObsidianRift", "SolarFlare", "QuantumAbyss", "StormCitadel", "CelestialForge" };
    private static readonly Color[] WorldColor = { new Color(0f,.93f,1f), new Color(1f,.18f,.28f), new Color(.22f,.64f,1f), new Color(1f,.12f,.82f), new Color(1f,.82f,.12f), new Color(.68f,.54f,1f), new Color(1f,.48f,.12f), new Color(.19f,.86f,1f), new Color(.35f,.68f,1f), new Color(1f,.88f,.48f) };
    private static readonly string[] ThemeNames = { "Cyan", "Green", "Violet", "Crimson", "Solar", "Ocean" };
    private static readonly Color[] ThemeColors = { new Color(0f,.93f,1f), new Color(.18f,1f,.53f), new Color(.73f,.39f,1f), new Color(1f,.16f,.30f), new Color(1f,.60f,.12f), new Color(.20f,.55f,1f) };
    private const int MaxStages = 100;
    private sealed class Target { public Kind kind; public GameObject go, bar, fill; public SpriteRenderer sr; public Vector3 origin; public float radius, phase, speed, hitDelay; public int hp, maxHp; public bool alive = true; }
    private sealed class Bolt { public GameObject go; public Target target; public float speed; public int damage; }
    private sealed class Hazard { public HazardKind kind; public GameObject go, exit; public Vector3 exitPos; public float t, period, originX, range; public bool active = true; }
    private sealed class Fx { public GameObject go; public float time, total; }

    private readonly List<Target> targets = new List<Target>();
    private readonly List<Bolt> bolts = new List<Bolt>();
    private readonly List<Hazard> hazards = new List<Hazard>();
    private readonly List<Fx> effects = new List<Fx>();
    private readonly Dictionary<string, Sprite> sprites = new Dictionary<string, Sprite>();
    private readonly Dictionary<string, AudioClip> clips = new Dictionary<string, AudioClip>();
    private Camera cam;
    private Canvas canvas;
    private RectTransform uiRoot;
    private Transform arena;
    private AudioSource music, sfx;
    private SpriteRenderer blade, shield;
    private readonly SpriteRenderer[] guns = new SpriteRenderer[2];
    private Text hudStage, hudScore, hudLives, hudCombo, hudPower, hudSlow;
    private Page page;
    private int stage = 1, world, selectedWorld, unlocked = 1, score, lives = 3, coins, theme, gunLevel = 1, bladeLevel, shieldLevel, slowLevel;
    private int bestCombo = 1, combo = 1, catchStreak, catches;
    private int[] stars = new int[MaxStages + 1], high = new int[MaxStages + 1];
    private bool musicOn = true, sfxOn = true, vibrateOn = true, multiCatch, held = true, flying, missed;
    private float shieldY, shieldH, launchIn, bladeAngle, stageTime, bladeSpeed = 7.4f, doubleTime, expandTime, stickyTime, slowMeter = 4f, slowTime, hazardLock, gunCooldown;
    private float shake, shakeTime, powerTime, hudTime;
    private string powerLabel = "";
    private Vector2 direction = Vector2.right;
    private Vector3 cameraHome;
    private float HH { get { return cam == null ? 5f : cam.orthographicSize; } }
    private float HW { get { return HH * (cam == null ? 16f / 9f : cam.aspect); } }
    private float L { get { return -HW + .16f; } }
    private float R { get { return HW - .16f; } }
    private float T { get { return HH - .16f; } }
    private float B { get { return -HH + .16f; } }
    private float SX { get { return L + .4f; } }
    private Color Accent { get { return ThemeColors[Mathf.Clamp(theme, 0, 5)]; } }

    private void Awake()
    {
        DontDestroyOnLoad(gameObject);
        Application.targetFrameRate = 60; Application.runInBackground = true;
        Screen.orientation = ScreenOrientation.LandscapeLeft; Screen.sleepTimeout = SleepTimeout.NeverSleep;
        LoadSave(); cam = Camera.main;
        if (cam == null) { GameObject c = new GameObject("Bounce Blade Camera"); cam = c.AddComponent<Camera>(); cam.tag = "MainCamera"; }
        cam.orthographic = true; cam.orthographicSize = 5f; cam.transform.position = new Vector3(0,0,-10); cam.clearFlags = CameraClearFlags.SolidColor;
        cameraHome = cam.transform.position;
        if (EventSystem.current == null) DontDestroyOnLoad(new GameObject("EventSystem", typeof(EventSystem), typeof(StandaloneInputModule)));
        GameObject cgo = new GameObject("Bounce Blade UI", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster)); DontDestroyOnLoad(cgo);
        canvas = cgo.GetComponent<Canvas>(); canvas.renderMode = RenderMode.ScreenSpaceOverlay; canvas.sortingOrder = 10;
        CanvasScaler scaler = cgo.GetComponent<CanvasScaler>(); scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize; scaler.referenceResolution = new Vector2(1280,720); scaler.matchWidthOrHeight = .5f;
        uiRoot = Rect("Page Root", canvas.transform); Stretch(uiRoot);
        GameObject ag = new GameObject("Arena"); arena = ag.transform; DontDestroyOnLoad(ag);
        music = gameObject.AddComponent<AudioSource>(); music.loop = true; music.volume = .45f;
        sfx = gameObject.AddComponent<AudioSource>();
        Show(Page.Menu);
    }

    private void Update()
    {
        float dt = Mathf.Min(Time.unscaledDeltaTime,.04f);
        if (page == Page.Game) TickGame(dt);
        TickEffects(dt); TickCamera(dt);
        if (page == Page.Game) ReadTouch();
        else if (Input.GetKeyDown(KeyCode.Escape)) Back();
    }

    private void LoadSave()
    {
        unlocked = Mathf.Clamp(PlayerPrefs.GetInt("bb.unlocked",1),1,MaxStages); coins = Mathf.Max(0,PlayerPrefs.GetInt("bb.coins",0));
        gunLevel = Mathf.Clamp(PlayerPrefs.GetInt("bb.gun",1),1,6); bladeLevel = Mathf.Clamp(PlayerPrefs.GetInt("bb.blade",0),0,5);
        shieldLevel = Mathf.Clamp(PlayerPrefs.GetInt("bb.shield",0),0,5); slowLevel = Mathf.Clamp(PlayerPrefs.GetInt("bb.slow",0),0,5);
        theme = Mathf.Clamp(PlayerPrefs.GetInt("bb.theme",0),0,5); musicOn = PlayerPrefs.GetInt("bb.music",1)!=0;
        sfxOn = PlayerPrefs.GetInt("bb.sfx",1)!=0; vibrateOn = PlayerPrefs.GetInt("bb.vibrate",1)!=0; multiCatch = PlayerPrefs.GetInt("bb.multicatch",0)!=0;
        selectedWorld = Mathf.Clamp(PlayerPrefs.GetInt("bb.world",0),0,9);
        for(int i=1;i<=MaxStages;i++){stars[i]=Mathf.Clamp(PlayerPrefs.GetInt("bb.star."+i,0),0,3);high[i]=Mathf.Max(0,PlayerPrefs.GetInt("bb.high."+i,0));}
    }

    private void Save()
    {
        PlayerPrefs.SetInt("bb.unlocked",unlocked); PlayerPrefs.SetInt("bb.coins",coins); PlayerPrefs.SetInt("bb.gun",gunLevel);
        PlayerPrefs.SetInt("bb.blade",bladeLevel); PlayerPrefs.SetInt("bb.shield",shieldLevel); PlayerPrefs.SetInt("bb.slow",slowLevel);
        PlayerPrefs.SetInt("bb.theme",theme); PlayerPrefs.SetInt("bb.music",musicOn?1:0); PlayerPrefs.SetInt("bb.sfx",sfxOn?1:0);
        PlayerPrefs.SetInt("bb.vibrate",vibrateOn?1:0); PlayerPrefs.SetInt("bb.multicatch",multiCatch?1:0); PlayerPrefs.SetInt("bb.world",selectedWorld);
        for(int i=1;i<=MaxStages;i++){PlayerPrefs.SetInt("bb.star."+i,stars[i]);PlayerPrefs.SetInt("bb.high."+i,high[i]);}
        PlayerPrefs.Save();
    }

    private void Show(Page p)
    {
        page=p; Clear(uiRoot);
        if(p!=Page.Game && p!=Page.Pause && p!=Page.Win && p!=Page.Lose) ClearArena();
        if(p!=Page.Game && p!=Page.Pause){if(p==Page.Menu) PlayMusic("Music & Ambience/Main Menu Theme"); else if(music!=null) music.Stop();}
        switch(p){case Page.Menu:Menu();break;case Page.Stages:StageSelect();break;case Page.Shop:Shop();break;case Page.Settings:Settings();break;case Page.Help:Help();break;case Page.Story:Story();break;case Page.Game:Hud();break;case Page.Pause:PausePage();break;case Page.Win:WinPage();break;case Page.Lose:LosePage();break;}
    }

    private void StartStage(int n)
    {
        stage=Mathf.Clamp(n,1,MaxStages); world=(stage-1)/10; selectedWorld=world; score=0;lives=3;combo=bestCombo=1;catchStreak=catches=0;stageTime=0;missed=false;
        shieldH=(1.65f+shieldLevel*.12f)*ShieldDifficulty();shieldY=0;launchIn=.7f;held=true;flying=false;direction=Vector2.right;
        doubleTime=expandTime=stickyTime=hazardLock=slowTime=gunCooldown=0;slowMeter=4f+slowLevel*1.4f;powerTime=0;powerLabel="";
        ClearArena(); BuildArena(); SpawnStage(); Show(Page.Game); PlayMusic("Music & Ambience/Gameplay Background"); PlaySfx("Core Gameplay SFX/Blade Launch");
    }

    private float ShieldDifficulty(){int d=PlayerPrefs.GetInt("bb.difficulty",1);return d==0?1.3f:d==2?.74f:1f;}
    private float BladeDifficulty(){int d=PlayerPrefs.GetInt("bb.difficulty",1);return (d==0?.9f:d==2?1.22f:1f)*(1f+bladeLevel*.08f);}

    private void ClearArena()
    {
        if(arena==null)return; Clear(arena); targets.Clear();bolts.Clear();hazards.Clear();effects.Clear();blade=null;shield=null;guns[0]=guns[1]=null;
        if(cam!=null)cam.transform.position=cameraHome;
    }

    private void BuildArena()
    {
        GameObject bg=WorldSprite("Backdrop","BounceBlade/Images/Backgrounds & Environments/Gameplay Background",Vector3.zero,new Vector2(HW*2.05f,HH*2.05f),-100,Color.white);
        bg.transform.position=new Vector3(0,0,5);
        WorldSprite("Left Wall","BounceBlade/Images/Gameplay Sprites/Wall Segment",new Vector3(L+.04f,0,0),new Vector2(.13f,HH*1.95f),2,Accent);
        shield=WorldSprite("Shield",null,new Vector3(SX,shieldY,0),new Vector2(.18f,shieldH),12,Accent).GetComponent<SpriteRenderer>();
        shield.sprite=MakeGenerated("shield",Color.white,16,128,true); Fit(shield,new Vector2(.18f,shieldH));
        blade=WorldSprite("Bounce Blade","BounceBlade/Images/Gameplay Sprites/Blade",new Vector3(SX+.45f,shieldY,0),new Vector2(.48f,.48f),20,Color.white).GetComponent<SpriteRenderer>();
        for(int i=0;i<2;i++)
        {
            Vector3 pos=new Vector3(L+1.05f,i==0?HH-1f:-HH+1f,0);
            guns[i]=WorldSprite("Mini-gun "+i,GunPath(world),pos,new Vector2(.78f,.5f),15,Accent).GetComponent<SpriteRenderer>();
            guns[i].enabled=gunLevel>0;
        }
    }

    private string GunPath(int w){return "BounceBlade/Images/Player Miniguns/Gameplay/Minigun_"+Slugs[Mathf.Clamp(w,0,9)];}
    private string MenuGunPath(int w){return "BounceBlade/Images/Player Miniguns/Menu/Minigun_"+Slugs[Mathf.Clamp(w,0,9)];}

    private GameObject WorldSprite(string name,string path,Vector3 pos,Vector2 size,int order,Color tint)
    {
        GameObject go=new GameObject(name); go.transform.SetParent(arena,false);go.transform.position=pos;
        SpriteRenderer sr=go.AddComponent<SpriteRenderer>(); sr.sprite=string.IsNullOrEmpty(path)?null:SpriteAt(path);
        if(sr.sprite==null)sr.sprite=MakeGenerated("solid",Color.white,16,16,false);
        sr.color=tint;sr.sortingOrder=order;Fit(sr,size);return go;
    }

    private void Fit(SpriteRenderer sr,Vector2 size)
    {
        if(sr==null||sr.sprite==null)return;Vector2 original=sr.sprite.bounds.size;
        if(original.x>0&&original.y>0)sr.transform.localScale=new Vector3(size.x/original.x,size.y/original.y,1);
    }

    private void SpawnStage()
    {
        int count=Mathf.Clamp(3+world*2+(stage%10)/2,3,16);float left=L+HW*.42f,right=R-.65f;
        for(int i=0;i<count;i++)
        {
            Kind k=stage%10==0&&i==0?Kind.Boss:stage>=7&&i%5==4?Kind.Turret:world>=3&&i%4==2?Kind.Slime:Kind.Drone;
            float x=Mathf.Lerp(left,right,(i+.5f)/count), y=Mathf.Lerp(T-.75f,B+.75f,(i%4)/3f)+UnityEngine.Random.Range(-.3f,.3f);
            AddTarget(k,new Vector3(x,y,0),i);
        }
        if(stage%3!=0)AddTarget(Kind.Crate,new Vector3(Mathf.Lerp(left,right,.65f),UnityEngine.Random.Range(B+1,T-1),0),count+1);
        if(stage>=4)AddHazard(HazardKind.Spikes,new Vector3(right-.3f,0,0));
        if(world>=1&&stage%2==0)AddHazard(HazardKind.Mover,new Vector3((left+right)*.54f,0,0));
        if(world>=2&&stage%3==0)AddHazard(HazardKind.Laser,new Vector3((left+right)*.64f,0,0));
        if(world>=3&&stage%2==1)AddHazard(HazardKind.Sticky,new Vector3((left+right)*.42f,UnityEngine.Random.Range(-2,2),0));
        if(world>=4&&stage%2==0)AddHazard(HazardKind.Portal,new Vector3(left+1.7f,2.3f,0));
        if(stage%2==0)AddHazard(HazardKind.Power,new Vector3(UnityEngine.Random.Range(left,right),UnityEngine.Random.Range(B+.8f,T-.8f),0));
        if(stage%5==0)AddHazard(HazardKind.Switch,new Vector3((left+right)*.5f,UnityEngine.Random.Range(-2.4f,2.4f),0));
    }

    private void AddTarget(Kind k,Vector3 pos,int idx)
    {
        int hp=k==Kind.Boss?12+world*2:k==Kind.Turret?6+stage/12:k==Kind.Slime?4+stage/20:k==Kind.Crate?3+world/2:3+stage/25;
        float r=k==Kind.Boss?.62f:k==Kind.Turret?.4f:k==Kind.Slime?.34f:k==Kind.Crate?.38f:.31f;
        string art=k==Kind.Turret||k==Kind.Boss?"Enemy Turret":k==Kind.Slime?"Enemy Slime":k==Kind.Crate?"Crate":"Enemy Drone";
        GameObject go=WorldSprite(k+" "+idx,"BounceBlade/Images/Gameplay Sprites/"+art,pos,new Vector2(2*r,2*r),8,Color.white);
        Target t=new Target{kind=k,go=go,sr=go.GetComponent<SpriteRenderer>(),origin=pos,hp=hp,maxHp=hp,radius=r,phase=UnityEngine.Random.Range(0,6.28f),speed=UnityEngine.Random.Range(.45f,1.2f)+stage*.008f};
        t.bar=WorldSprite("Health Back",null,pos,new Vector2(2*r,.065f),30,new Color(.04f,.05f,.1f));
        t.fill=WorldSprite("Health Fill",null,pos,new Vector2(2*r,.05f),31,Accent);targets.Add(t);HealthBar(t);
    }

    private void AddHazard(HazardKind k,Vector3 p)
    {
        string art=k==HazardKind.Portal?"Portal":k==HazardKind.Switch?"Switch":k==HazardKind.Power?"../UI Elements/Timer Icon":k==HazardKind.Mover?"Crate":"Wall Segment";
        string path=k==HazardKind.Power?"BounceBlade/Images/UI Elements/Timer Icon":"BounceBlade/Images/Gameplay Sprites/"+art;
        Vector2 size=k==HazardKind.Spikes?new Vector2(.25f,HH*1.35f):k==HazardKind.Laser?new Vector2(.1f,HH*1.6f):k==HazardKind.Sticky?new Vector2(.35f,1.1f):k==HazardKind.Portal?new Vector2(.72f,.72f):k==HazardKind.Switch||k==HazardKind.Power?new Vector2(.36f,.36f):new Vector2(.62f,.62f);
        GameObject go=WorldSprite("Hazard "+k,path,p,size,6,k==HazardKind.Power?WorldColor[4]:k==HazardKind.Switch?WorldColor[1]:WorldColor[3]);
        Hazard h=new Hazard{kind=k,go=go,period=k==HazardKind.Laser?2.4f:3.2f,t=UnityEngine.Random.Range(0,2f),originX=p.x,range=1.3f};
        if(k==HazardKind.Portal){h.exitPos=new Vector3(R-1,-p.y,0);h.exit=WorldSprite("Portal Exit",path,h.exitPos,size,6,WorldColor[2]);}
        hazards.Add(h);
    }

    private void ReadTouch()
    {
        if(Input.touchCount>=2){if(slowMeter>0){slowTime=.45f;slowMeter=Mathf.Max(0,slowMeter-Time.unscaledDeltaTime);}return;}slowTime=0;
        bool pressed=Input.GetMouseButton(0),over=false;Vector2 pos;
        if(Input.touchCount>0){Touch t=Input.GetTouch(0);pos=t.position;over=EventSystem.current!=null&&EventSystem.current.IsPointerOverGameObject(t.fingerId);pressed=t.phase==TouchPhase.Began||t.phase==TouchPhase.Moved||t.phase==TouchPhase.Stationary;}
        else{pos=Input.mousePosition;over=EventSystem.current!=null&&EventSystem.current.IsPointerOverGameObject();}
        if(pressed&&!over){Vector3 wp=cam.ScreenToWorldPoint(new Vector3(pos.x,pos.y,10));shieldY=Mathf.Clamp(wp.y,B+shieldH*.5f,T-shieldH*.5f);}
    }

    private void TickGame(float unscaled)
    {
        float dt=unscaled*(slowTime>0&&slowMeter>0?.38f:1f);stageTime+=dt;
        doubleTime=Mathf.Max(0,doubleTime-dt);expandTime=Mathf.Max(0,expandTime-dt);stickyTime=Mathf.Max(0,stickyTime-dt);hazardLock=Mathf.Max(0,hazardLock-dt);powerTime=Mathf.Max(0,powerTime-dt);
        if(powerTime<=0)powerLabel="";
        if(slowTime<=0)slowMeter=Mathf.Min(4f+slowLevel*1.4f,slowMeter+dt*.32f);
        float sh=shieldH+(expandTime>0?.7f:0);shieldY=Mathf.Clamp(shieldY,B+sh*.5f,T-sh*.5f);
        if(shield!=null){shield.transform.position=new Vector3(SX,shieldY,0);Fit(shield,new Vector2(.18f,sh));shield.color=expandTime>0?WorldColor[4]:Accent;}
        TickTargets(dt);TickHazards(dt);TickGuns(dt);TickBolts(dt);TickBlade(dt);
        hudTime-=unscaled;if(hudTime<=0){UpdateHud();hudTime=.08f;}
    }

    private void TickTargets(float dt)
    {
        foreach(Target t in targets)
        {
            if(!t.alive||t.go==null)continue;t.hitDelay=Mathf.Max(0,t.hitDelay-dt);t.phase+=dt*t.speed;Vector3 p=t.go.transform.position;
            if(t.kind==Kind.Drone){p.x+=Mathf.Sin(t.phase)*dt*.45f;p.y=t.origin.y+Mathf.Sin(t.phase*1.6f)*(.25f+world*.025f);t.go.transform.Rotate(0,0,28*dt);}
            else if(t.kind==Kind.Slime){p.y=t.origin.y+Mathf.Abs(Mathf.Sin(t.phase*1.2f))*(.6f+world*.04f)-.25f;p.x=t.origin.x+Mathf.Sin(t.phase*.7f)*.28f;}
            else if(t.kind==Kind.Boss){p.y=t.origin.y+Mathf.Sin(t.phase*.75f)*.8f;p.x=t.origin.x+Mathf.Sin(t.phase*.4f)*.45f;}
            t.go.transform.position=p;HealthBar(t);
        }
    }

    private void HealthBar(Target t)
    {
        if(t.go==null||t.bar==null||t.fill==null)return;float w=t.radius*2f,f=Mathf.Clamp01((float)t.hp/t.maxHp);Vector3 p=t.go.transform.position+new Vector3(0,-t.radius-.14f,0);
        t.bar.transform.position=p;t.fill.transform.position=p+new Vector3(-w*.5f+w*f*.5f,0,-.01f);SpriteRenderer sr=t.fill.GetComponent<SpriteRenderer>();sr.color=f<.34f?WorldColor[3]:Accent;Fit(sr,new Vector2(Mathf.Max(.012f,w*f),.05f));
    }

    private void TickHazards(float dt)
    {
        foreach(Hazard h in hazards)
        {
            if(!h.active||h.go==null)continue;h.t+=dt;
            if(h.kind==HazardKind.Mover){Vector3 p=h.go.transform.position;p.x=h.originX+Mathf.Sin(h.t*1.1f)*h.range;h.go.transform.position=p;h.go.transform.Rotate(0,0,45*dt);}
            if(h.kind==HazardKind.Laser){bool on=h.t%h.period>h.period*.42f;h.go.GetComponent<SpriteRenderer>().color=on?WorldColor[3]:new Color(.45f,.3f,.65f,.25f);}
            if(h.kind==HazardKind.Power)h.go.transform.Rotate(0,0,45*dt);
        }
    }

    private Target Closest(Vector3 from,float y,bool ignoreCrates)
    {
        Target best=null;float vbest=float.MaxValue;
        foreach(Target t in targets){if(!t.alive||t.go==null||(ignoreCrates&&t.kind==Kind.Crate))continue;Vector3 p=t.go.transform.position;float v=(p-from).sqrMagnitude+Mathf.Abs(p.y-y)*.45f;if(v<vbest){vbest=v;best=t;}}
        return best;
    }

    private void TickGuns(float dt)
    {
        if(gunLevel<=0)return;gunCooldown-=dt;
        for(int i=0;i<guns.Length;i++)
        {
            SpriteRenderer gun=guns[i];if(gun==null||!gun.enabled)continue;Target t=Closest(gun.transform.position,i==0?T-.8f:B+.8f,true);if(t==null)continue;
            Vector2 v=t.go.transform.position-gun.transform.position;gun.transform.rotation=Quaternion.Euler(0,0,Mathf.Atan2(v.y,v.x)*Mathf.Rad2Deg);
            if(gunCooldown<=0&&i==1){Shoot(gun.transform.position,t,Mathf.Max(1,Mathf.CeilToInt(t.maxHp/3f)));gunCooldown=Mathf.Max(.22f,.72f-gunLevel*.075f);}
        }
    }

    private void Shoot(Vector3 p,Target t,int dmg){GameObject go=WorldSprite("Mini-gun Bolt",null,p,new Vector2(.12f,.12f),22,WorldColor[0]);bolts.Add(new Bolt{go=go,target=t,speed=8+gunLevel*.65f,damage=dmg});}
    private void TickBolts(float dt)
    {
        for(int i=bolts.Count-1;i>=0;i--){Bolt b=bolts[i];if(b.go==null||b.target==null||!b.target.alive||b.target.go==null){if(b.go!=null)Destroy(b.go);bolts.RemoveAt(i);continue;}
            Vector3 p=b.go.transform.position,to=b.target.go.transform.position,d=to-p;if(d.magnitude<=b.speed*dt+b.target.radius){Damage(b.target,b.damage);FxAt(to,"BounceBlade/Images/Effects & Particles/Impact Spark",.32f,.2f);Destroy(b.go);bolts.RemoveAt(i);}else b.go.transform.position=p+d.normalized*b.speed*dt;}
    }

    private void TickBlade(float dt)
    {
        if(blade==null)return;if(held){launchIn-=dt;blade.transform.position=new Vector3(SX+.44f,shieldY,0);bladeAngle+=dt*260;blade.transform.rotation=Quaternion.Euler(0,0,bladeAngle);if(launchIn<=0)Launch();return;}
        if(!flying)return;Vector3 p=blade.transform.position;p+=(Vector3)(direction*(bladeSpeed*BladeDifficulty()*(stickyTime>0?.46f:1f))*dt);bladeAngle+=dt*900;blade.transform.rotation=Quaternion.Euler(0,0,bladeAngle);
        if(p.y>=T){p.y=T;direction.y=-Mathf.Abs(direction.y);WallFx(p);}if(p.y<=B){p.y=B;direction.y=Mathf.Abs(direction.y);WallFx(p);}if(p.x>=R){p.x=R;direction.x=-Mathf.Abs(direction.x);WallFx(p);}
        CheckHazards(ref p);if(page!=Page.Game)return;
        foreach(Target t in targets){if(!t.alive||t.go==null||t.hitDelay>0)continue;Vector3 ep=t.go.transform.position;Vector2 v=p-ep;float rr=t.radius+BladeRadius;if(v.sqrMagnitude>rr*rr)continue;
            p=ep+(Vector3)((v.sqrMagnitude<.0001f?Vector2.right:v.normalized)*(rr+.03f));t.hitDelay=.25f;direction.y=Mathf.Clamp(direction.y+UnityEngine.Random.Range(-.16f,.16f),-.88f,.88f);
            Damage(t,Mathf.Max(1,Mathf.CeilToInt(t.maxHp/3f)));combo=Mathf.Clamp(combo+1,1,10);bestCombo=Mathf.Max(bestCombo,combo);score+=50*combo*(doubleTime>0?2:1);
            FxAt(ep,"BounceBlade/Images/Effects & Particles/Impact Spark",.6f,.28f);if(t.kind!=Kind.Crate)FxAt(ep,"BounceBlade/Images/Effects & Particles/Slice Effect",.72f,.35f);
            PlaySfx("Core Gameplay SFX/Enemy Slice (Variation 1)");shake=.08f;shakeTime=.1f;if(vibrateOn)Handheld.Vibrate();break;}
        blade.transform.position=p;if(Pickup(p))return;
        if(direction.x<0&&p.x<=SX+.45f){if(Mathf.Abs(p.y-shieldY)<=(shieldH+(expandTime>0?.7f:0))*.5f+BladeRadius)Catch();else if(p.x<L-.18f)Miss();}
        if(p.x<L-.6f)Miss();
    }

    private void Launch()
    {
        Target t=Closest(blade.transform.position,shieldY,false);Vector2 v=t!=null?(Vector2)(t.go.transform.position-blade.transform.position).normalized:new Vector2(1,UnityEngine.Random.Range(-.22f,.22f)).normalized;
        if(v.x<.4f)v.x=.4f;direction=v.normalized;held=false;flying=true;PlaySfx("Core Gameplay SFX/Blade Launch");
    }

    private void Damage(Target t,int damage)
    {
        if(t==null||!t.alive)return;t.hp-=damage;if(t.sr!=null)t.sr.color=t.hp>0?Color.Lerp(Color.white,Accent,.55f):Color.white;if(t.hp>0)return;
        t.hp=0;t.alive=false;score+=t.kind==Kind.Boss?500:t.kind==Kind.Crate?80:100;FxAt(t.go.transform.position,"BounceBlade/Images/Effects & Particles/Explosion",t.kind==Kind.Boss?1.4f:.8f,.55f);
        t.go.SetActive(false);t.bar.SetActive(false);t.fill.SetActive(false);PlaySfx(t.kind==Kind.Crate?"Core Gameplay SFX/Crate Break":"Core Gameplay SFX/Enemy Slice (Variation 2)");
    }

    private void Catch()
    {
        flying=false;held=true;launchIn=.48f;catches++;catchStreak++;score+=25*catchStreak;if(multiCatch){score+=25;slowMeter=Mathf.Min(4f+slowLevel*1.4f,slowMeter+.25f);}
        PlaySfx("Core Gameplay SFX/Wall Ricochet (Variation 1)");if(catchStreak>=2)PlaySfx("UI & Feedback SFX/Combo - Streak");
        FxAt(new Vector3(SX,shieldY,0),"BounceBlade/Images/Effects & Particles/Impact Spark",.9f,.36f);if(vibrateOn)Handheld.Vibrate();if(CountTargets()==0)Complete();
    }

    private void Miss()
    {
        if(!flying)return;flying=false;held=true;launchIn=.75f;lives--;catchStreak=0;combo=1;missed=true;PlaySfx("Core Gameplay SFX/Level Fail - Death");
        FxAt(blade.transform.position,"BounceBlade/Images/Effects & Particles/Explosion",.9f,.45f);shake=.25f;shakeTime=.2f;if(vibrateOn)Handheld.Vibrate();if(lives<=0)Fail();else UpdateHud();
    }

    private int CountTargets(){int n=0;foreach(Target t in targets)if(t.alive)n++;return n;}
    private void WallFx(Vector3 p){PlaySfx("Core Gameplay SFX/Wall Ricochet (Variation 1)");FxAt(p,"BounceBlade/Images/Effects & Particles/Impact Spark",.25f,.15f);}

    private void CheckHazards(ref Vector3 p)
    {
        if(hazardLock>0)return;
        foreach(Hazard h in hazards)
        {
            if(!h.active||h.go==null)continue;Vector3 hp=h.go.transform.position;
            if(h.kind==HazardKind.Portal)
            {
                if(Vector2.Distance(p,hp)<.48f){p=h.exitPos+new Vector3(-.5f,0,0);blade.transform.position=p;hazardLock=.45f;PlaySfx("Core Gameplay SFX/Portal Enter");FxAt(hp,"BounceBlade/Images/Effects & Particles/Portal Enter Effect",.8f,.45f);return;}
                if(Vector2.Distance(p,h.exitPos)<.48f){p=hp+new Vector3(.5f,0,0);blade.transform.position=p;hazardLock=.45f;PlaySfx("Core Gameplay SFX/Portal Enter");return;}
            }
            if(h.kind==HazardKind.Switch&&Vector2.Distance(p,hp)<.42f){h.active=false;h.go.SetActive(false);score+=150;PlaySfx("Core Gameplay SFX/Switch Activation");foreach(Hazard other in hazards)if(other.kind==HazardKind.Laser){other.active=false;other.go.SetActive(false);}FxAt(hp,"BounceBlade/Images/Effects & Particles/Impact Spark",.7f,.3f);return;}
            float dist=h.kind==HazardKind.Sticky?.6f:h.kind==HazardKind.Spikes?.5f:h.kind==HazardKind.Laser?.24f:h.kind==HazardKind.Mover?.48f:.2f;
            if(Vector2.Distance(p,hp)>dist||h.kind==HazardKind.Power||h.kind==HazardKind.Switch)continue;hazardLock=.35f;
            if(h.kind==HazardKind.Sticky){stickyTime=1.2f;direction*=.7f;}else{direction.x=p.x<hp.x?-Mathf.Abs(direction.x):Mathf.Abs(direction.x);direction.y=Mathf.Clamp(direction.y+UnityEngine.Random.Range(-.35f,.35f),-.9f,.9f);PlaySfx("Core Gameplay SFX/Wall Ricochet (Variation 2)");}
            FxAt(p,"BounceBlade/Images/Effects & Particles/Impact Spark",.45f,.22f);return;
        }
    }

    private bool Pickup(Vector3 p)
    {
        foreach(Hazard h in hazards)
        {
            if(!h.active||h.kind!=HazardKind.Power||h.go==null||Vector2.Distance(p,h.go.transform.position)>.55f)continue;
            h.active=false;h.go.SetActive(false);int power=(stage+world)%4;
            if(power==0){expandTime=10;powerLabel="SHIELD EXPANDED";}else if(power==1){doubleTime=12;powerLabel="SCORE x2";}else if(power==2){slowMeter=Mathf.Min(5f+slowLevel*1.4f,slowMeter+2);powerLabel="SLOW TIME REFILLED";}else{lives=Mathf.Min(3,lives+1);powerLabel="EXTRA LIFE";}
            powerTime=2.5f;PlaySfx("Optional - Extra Sounds/Power-Up Collect");FxAt(p,"BounceBlade/Images/Effects & Particles/Portal Enter Effect",.8f,.45f);return true;
        }
        return false;
    }

    private void FxAt(Vector3 p,string path,float size,float duration)
    {
        GameObject go=WorldSprite("Impact FX",path,p,new Vector2(size,size),40,Color.white);effects.Add(new Fx{go=go,time=duration,total=duration});
    }

    private void TickEffects(float dt)
    {
        for(int i=effects.Count-1;i>=0;i--){Fx fx=effects[i];fx.time-=dt;if(fx.go!=null){SpriteRenderer sr=fx.go.GetComponent<SpriteRenderer>();if(sr!=null)sr.color=new Color(1,1,1,Mathf.Clamp01(fx.time/Mathf.Max(.01f,fx.total)));fx.go.transform.Rotate(0,0,140*dt);}if(fx.time<=0){if(fx.go!=null)Destroy(fx.go);effects.RemoveAt(i);}}
    }

    private void TickCamera(float dt)
    {
        if(cam==null)return;if(shakeTime>0){shakeTime-=dt;cam.transform.position=cameraHome+new Vector3(UnityEngine.Random.Range(-shake,shake),UnityEngine.Random.Range(-shake,shake),0);}else cam.transform.position=cameraHome;
    }

    private void Complete()
    {
        if(page!=Page.Game)return;int earned=1;if(lives>=2&&!missed)earned++;if(lives>=3&&bestCombo>=4&&stageTime<90)earned++;
        stars[stage]=Mathf.Max(stars[stage],earned);high[stage]=Mathf.Max(high[stage],score);unlocked=Mathf.Max(unlocked,Mathf.Min(MaxStages,stage+1));coins+=80+stage*4+score/20+stars[stage]*35;
        Save();PlaySfx("Core Gameplay SFX/Level Complete");Show(Page.Win);PlayMusic("Music & Ambience/Victory Theme");
    }

    private void Fail(){if(page!=Page.Game)return;Show(Page.Lose);PlayMusic("Music & Ambience/Game Over Theme");}
    private void UpdateHud()
    {
        if(hudStage!=null)hudStage.text=Worlds[world].ToUpperInvariant()+"  •  "+stage+" / 100";
        if(hudScore!=null)hudScore.text="SCORE "+score.ToString("D6")+"   •   SHARDS "+coins;
        if(hudLives!=null)hudLives.text="LIVES  "+new string('♥',Mathf.Max(0,lives))+new string('♡',Mathf.Max(0,3-lives));
        if(hudCombo!=null)hudCombo.text="COMBO x"+combo;if(hudPower!=null)hudPower.text=powerTime>0?powerLabel:"";
        if(hudSlow!=null)hudSlow.text="HOLD TWO FINGERS TO SLOW TIME   "+slowMeter.ToString("0.0")+"s";
    }

    private void Back(){if(page==Page.Game)Show(Page.Pause);else if(page==Page.Pause)Show(Page.Game);else if(page!=Page.Menu)Show(Page.Menu);else Application.Quit();}
    private void PlayMusic(string path){if(!musicOn||music==null)return;AudioClip c=AudioAt("BounceBlade/SoundTrack/"+path);if(c==null)return;if(music.clip==c&&music.isPlaying)return;music.Stop();music.clip=c;music.loop=true;music.Play();}
    private void PlaySfx(string path){if(sfxOn&&sfx!=null){AudioClip c=AudioAt("BounceBlade/SoundTrack/"+path);if(c!=null)sfx.PlayOneShot(c,.8f);}}
    private AudioClip AudioAt(string path){AudioClip c;if(clips.TryGetValue(path,out c))return c;c=Resources.Load<AudioClip>(path);clips[path]=c;return c;}

    private Sprite SpriteAt(string path)
    {
        Sprite result;if(sprites.TryGetValue(path,out result))return result;Texture2D tex=Resources.Load<Texture2D>(path);
        if(tex==null){sprites[path]=null;return null;}result=Sprite.Create(tex,new Rect(0,0,tex.width,tex.height),new Vector2(.5f,.5f),Mathf.Max(1,Mathf.Max(tex.width,tex.height)/8f));result.name=path;sprites[path]=result;return result;
    }

    private Sprite MakeGenerated(string key,Color color,int w,int h,bool glow)
    {
        string id="__"+key;Sprite cached;if(sprites.TryGetValue(id,out cached))return cached;
        Texture2D tex=new Texture2D(w,h,TextureFormat.RGBA32,false);tex.name=id;tex.filterMode=FilterMode.Bilinear;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){float edge=Mathf.Min(Mathf.Min((x+.5f)/w,1f-(x+.5f)/w),Mathf.Min((y+.5f)/h,1f-(y+.5f)/h));float a=glow?Mathf.Clamp01(edge*Mathf.Min(w,h)*.5f):1f;tex.SetPixel(x,y,new Color(color.r,color.g,color.b,color.a*a));}
        tex.Apply(false,true);cached=Sprite.Create(tex,new Rect(0,0,w,h),new Vector2(.5f,.5f),32);cached.name=id;sprites[id]=cached;return cached;
    }

    private RectTransform Rect(string name,Transform parent){GameObject g=new GameObject(name,typeof(RectTransform));RectTransform r=g.GetComponent<RectTransform>();r.SetParent(parent,false);r.anchorMin=r.anchorMax=r.pivot=new Vector2(.5f,.5f);return r;}
    private void Stretch(RectTransform r){r.anchorMin=Vector2.zero;r.anchorMax=Vector2.one;r.offsetMin=r.offsetMax=Vector2.zero;}
    private void Clear(Transform root){if(root==null)return;for(int i=root.childCount-1;i>=0;i--)Destroy(root.GetChild(i).gameObject);}

    private Image Image(string name,Transform parent,string path,Vector2 pos,Vector2 size,bool preserve=false)
    {
        RectTransform r=Rect(name,parent);r.anchoredPosition=pos;r.sizeDelta=size;Image im=r.gameObject.AddComponent<Image>();im.sprite=SpriteAt(path);im.preserveAspect=preserve;im.raycastTarget=false;if(im.sprite==null)im.color=new Color(.03f,.08f,.14f,.85f);return im;
    }
    private void Background(string path,float shade)
    {
        RectTransform r=Rect("Background",uiRoot);Stretch(r);Image im=r.gameObject.AddComponent<Image>();im.sprite=SpriteAt(path);im.raycastTarget=false;
        RectTransform shadeRect=Rect("Background shade",uiRoot);Stretch(shadeRect);Image dim=shadeRect.gameObject.AddComponent<Image>();dim.color=new Color(.01f,.015f,.04f,shade);dim.raycastTarget=false;
    }
    private Text Text(string value,Transform parent,Vector2 pos,Vector2 size,int font,TextAnchor align=TextAnchor.MiddleCenter,Color? color=null)
    {
        RectTransform r=Rect("Text",parent);r.anchoredPosition=pos;r.sizeDelta=size;Text t=r.gameObject.AddComponent<Text>();t.text=value;t.font=Resources.GetBuiltinResource<Font>("Arial.ttf");t.fontSize=font;t.fontStyle=FontStyle.Bold;t.alignment=align;t.color=color??Color.white;t.horizontalOverflow=HorizontalWrapMode.Wrap;t.verticalOverflow=VerticalWrapMode.Overflow;t.raycastTarget=false;
        Shadow sh=r.gameObject.AddComponent<Shadow>();Color c=color??Accent;sh.effectColor=new Color(c.r,c.g,c.b,.55f);sh.effectDistance=new Vector2(1,-1);return t;
    }
    private Button Button(string title,Transform parent,Vector2 pos,Vector2 size,Action action,Color? tint=null,bool primary=false,bool enabled=true)
    {
        RectTransform r=Rect("Button "+title,parent);r.anchoredPosition=pos;r.sizeDelta=size;Image im=r.gameObject.AddComponent<Image>();im.sprite=SpriteAt("BounceBlade/Images/UI Elements/Button");im.color=enabled?(tint??Accent):new Color(.35f,.38f,.43f,.55f);im.raycastTarget=true;
        Button b=r.gameObject.AddComponent<Button>();b.targetGraphic=im;b.interactable=enabled;RectTransform tr=Rect("Label",r);Stretch(tr);tr.offsetMin=new Vector2(8,3);tr.offsetMax=new Vector2(-8,-3);
        Text label=tr.gameObject.AddComponent<Text>();label.text=title;label.font=Resources.GetBuiltinResource<Font>("Arial.ttf");label.fontSize=primary?24:18;label.fontStyle=FontStyle.Bold;label.alignment=TextAnchor.MiddleCenter;label.color=enabled?Color.white:new Color(.55f,.62f,.69f);label.resizeTextForBestFit=true;label.resizeTextMinSize=11;label.resizeTextMaxSize=primary?27:21;label.raycastTarget=false;
        b.onClick.AddListener(()=>{PlaySfx("UI & Feedback SFX/Button Click");if(action!=null)action();});return b;
    }
    private void Header(string title,string sub=""){Text(title,uiRoot,new Vector2(0,292),new Vector2(1100,62),40,TextAnchor.MiddleCenter,Accent);if(sub.Length>0)Text(sub,uiRoot,new Vector2(0,250),new Vector2(1100,34),17,TextAnchor.MiddleCenter,new Color(.77f,.88f,.94f));}

    private void Menu()
    {
        Background("BounceBlade/Images/Backgrounds & Environments/Menu Background",.54f);
        Image("Game logo",uiRoot,"BounceBlade/Images/App Icon & Logo/Game Logo",new Vector2(0,208),new Vector2(560,138),true);
        Text("CATCH THE LIGHT  •  SAVE THE GRID",uiRoot,new Vector2(0,128),new Vector2(850,34),20,TextAnchor.MiddleCenter,Accent);
        Button("START CAMPAIGN",uiRoot,new Vector2(-205,50),new Vector2(390,70),()=>StartStage(unlocked),Accent,true);
        Button("STAGE SELECT",uiRoot,new Vector2(205,50),new Vector2(390,70),()=>Show(Page.Stages),WorldColor[2],true);
        Button("MINI-GUN SHOP",uiRoot,new Vector2(-205,-39),new Vector2(390,62),()=>Show(Page.Shop),WorldColor[4]);
        Button("SETTINGS",uiRoot,new Vector2(205,-39),new Vector2(390,62),()=>Show(Page.Settings),WorldColor[0]);
        Button("HOW TO PLAY",uiRoot,new Vector2(-205,-118),new Vector2(390,58),()=>Show(Page.Help),WorldColor[3]);
        Button("THE STORY",uiRoot,new Vector2(205,-118),new Vector2(390,58),()=>Show(Page.Story),WorldColor[5]);
        int clears=0,total=0;for(int i=1;i<=MaxStages;i++){if(stars[i]>0)clears++;total+=stars[i];}
        Text("PROGRESS "+clears+"/100   •   "+total+"/300 STARS   •   "+coins+" SHARDS",uiRoot,new Vector2(0,-220),new Vector2(1000,32),18,TextAnchor.MiddleCenter,new Color(.73f,.85f,.92f));
        Text("100 STAGES  •  10 WORLDS  •  OFFLINE ARCADE",uiRoot,new Vector2(0,-270),new Vector2(1000,26),14,TextAnchor.MiddleCenter,new Color(.45f,.64f,.73f));
    }

    private void StageSelect()
    {
        Background("BounceBlade/Images/UI Elements/Main Menu Background",.74f);Button("← BACK",uiRoot,new Vector2(-550,306),new Vector2(145,50),()=>Show(Page.Menu),Color.gray);Header("STAGE SELECT","Choose a world, then replay unlocked stages");
        for(int i=0;i<10;i++){int w=i;Button(Worlds[i],uiRoot,new Vector2(-550+i*122.2f,205),new Vector2(116,52),()=>{selectedWorld=w;Save();Show(Page.Stages);},WorldColor[i],selectedWorld==i);}
        int first=selectedWorld*10+1;Text(Worlds[selectedWorld].ToUpperInvariant()+"  •  STAGES "+first+"–"+(first+9),uiRoot,new Vector2(0,157),new Vector2(1080,30),18,TextAnchor.MiddleCenter,WorldColor[selectedWorld]);
        for(int i=0;i<10;i++){int n=first+i,col=i%5,row=i/5;float x=-430+col*215,y=78-row*100;bool ok=n<=unlocked;string label=ok?"STAGE "+n+"\n"+(stars[n]>0?new string('★',stars[n]):"READY"):"LOCKED";
            Button(label,uiRoot,new Vector2(x,y),new Vector2(184,72),()=>{if(ok)StartStage(n);},WorldColor[selectedWorld],n==unlocked,ok);
            if(high[n]>0)Text("BEST "+high[n],uiRoot,new Vector2(x,y-42),new Vector2(184,22),12,TextAnchor.MiddleCenter,new Color(.7f,.81f,.87f));}
        Text("UNLOCKED THROUGH STAGE "+unlocked+"  •  "+coins+" SHARDS",uiRoot,new Vector2(0,-225),new Vector2(900,30),16,TextAnchor.MiddleCenter,Accent);
        Button("PLAY LATEST",uiRoot,new Vector2(0,-285),new Vector2(290,50),()=>StartStage(unlocked),Accent,true);
    }

    private void Shop()
    {
        Background("BounceBlade/Images/Backgrounds & Environments/Menu Background",.66f);Button("← BACK",uiRoot,new Vector2(-550,306),new Vector2(145,50),()=>Show(Page.Menu),Color.gray);Header("MINI-GUN & ABILITY SHOP","Clear stages to earn shards; purchases save locally");
        Text("SHARDS: "+coins,uiRoot,new Vector2(-80,205),new Vector2(650,36),22,TextAnchor.MiddleCenter,WorldColor[4]);Image("World mini-gun skin",uiRoot,MenuGunPath(world),new Vector2(470,205),new Vector2(170,82),true);
        ShopItem("TWIN MINI-GUNS","Fire rate • Level "+gunLevel+"/6","gun",new Vector2(-280,100),Mathf.Min(gunLevel,5)*125+175);
        ShopItem("BLADE DRIVE","Launch speed • Level "+bladeLevel+"/5","blade",new Vector2(280,100),bladeLevel*140+160);
        ShopItem("SHIELD AMPLIFIER","Catch area • Level "+shieldLevel+"/5","shield",new Vector2(-280,-50),shieldLevel*130+150);
        ShopItem("SLOW-TIME CAPACITOR","Slow meter • Level "+slowLevel+"/5","slow",new Vector2(280,-50),slowLevel*135+175);
        Button(multiCatch?"MULTI-CATCH UNLOCKED":"UNLOCK MULTI-CATCH • 700",uiRoot,new Vector2(0,-205),new Vector2(360,54),BuyMultiCatch,multiCatch?WorldColor[1]:WorldColor[3],multiCatch,!multiCatch&&coins>=700);
        Text("Mini-gun art changes with each world",uiRoot,new Vector2(0,-273),new Vector2(1000,26),14,TextAnchor.MiddleCenter,new Color(.7f,.82f,.89f));
    }

    private void ShopItem(string title,string detail,string key,Vector2 pos,int cost)
    {
        Image("Shop Panel",uiRoot,"BounceBlade/Images/UI Elements/UI Panel",pos,new Vector2(500,124));
        Text(title,uiRoot,pos+new Vector2(0,32),new Vector2(440,28),19,TextAnchor.MiddleCenter,Accent);
        Text(detail,uiRoot,pos+new Vector2(0,2),new Vector2(460,25),14);
        int lv=key=="gun"?gunLevel:key=="blade"?bladeLevel:key=="shield"?shieldLevel:slowLevel,max=key=="gun"?6:5;
        Button(lv>=max?"MAX LEVEL":"UPGRADE • "+cost+" SHARDS",uiRoot,pos+new Vector2(0,-34),new Vector2(265,40),()=>BuyUpgrade(key,cost),lv>=max?Color.gray:WorldColor[4],false,lv<max&&coins>=cost);
    }
    private void BuyUpgrade(string key,int cost)
    {
        int lv=key=="gun"?gunLevel:key=="blade"?bladeLevel:key=="shield"?shieldLevel:slowLevel,max=key=="gun"?6:5;if(lv>=max||coins<cost)return;coins-=cost;
        if(key=="gun")gunLevel++;else if(key=="blade")bladeLevel++;else if(key=="shield")shieldLevel++;else slowLevel++;
        Save();PlaySfx("Optional - Extra Sounds/Power-Up Collect");Show(Page.Shop);
    }
    private void BuyMultiCatch(){if(multiCatch||coins<700)return;coins-=700;multiCatch=true;Save();Show(Page.Shop);}

    private void Settings()
    {
        Background("BounceBlade/Images/UI Elements/Main Menu Background",.77f);Button("← BACK",uiRoot,new Vector2(-550,306),new Vector2(145,50),()=>Show(Page.Menu),Color.gray);Header("SETTINGS","Themes, difficulty and device feedback");
        Text("COLOR THEME",uiRoot,new Vector2(0,205),new Vector2(850,28),18,TextAnchor.MiddleCenter,Accent);
        for(int i=0;i<6;i++){int id=i;Button(ThemeNames[i].ToUpperInvariant(),uiRoot,new Vector2(-450+i*180,160),new Vector2(166,48),()=>{theme=id;Save();Show(Page.Settings);},ThemeColors[i],theme==i);}
        Text("DIFFICULTY",uiRoot,new Vector2(0,108),new Vector2(650,26),17,TextAnchor.MiddleCenter,Accent);string[] d={"EASY","NORMAL","HARD"};
        for(int i=0;i<3;i++){int pick=i;Button(d[i],uiRoot,new Vector2(-180+i*180,76),new Vector2(164,45),()=>{PlayerPrefs.SetInt("bb.difficulty",pick);Save();Show(Page.Settings);},WorldColor[i+1],PlayerPrefs.GetInt("bb.difficulty",1)==i);}
        Button("MUSIC: "+(musicOn?"ON":"OFF"),uiRoot,new Vector2(-260,-5),new Vector2(300,56),()=>{musicOn=!musicOn;Save();if(musicOn)PlayMusic("Music & Ambience/Main Menu Theme");else music.Stop();Show(Page.Settings);},Accent);
        Button("SOUND FX: "+(sfxOn?"ON":"OFF"),uiRoot,new Vector2(260,-5),new Vector2(300,56),()=>{sfxOn=!sfxOn;Save();Show(Page.Settings);},WorldColor[3]);
        Button("VIBRATION: "+(vibrateOn?"ON":"OFF"),uiRoot,new Vector2(-260,-80),new Vector2(300,56),()=>{vibrateOn=!vibrateOn;Save();Show(Page.Settings);},WorldColor[2]);
        Button("RESET CAMPAIGN SAVE",uiRoot,new Vector2(260,-80),new Vector2(300,56),ResetSave,WorldColor[1]);
    }
    private void ResetSave(){for(int i=1;i<=MaxStages;i++){PlayerPrefs.DeleteKey("bb.star."+i);PlayerPrefs.DeleteKey("bb.high."+i);stars[i]=high[i]=0;}foreach(string k in new[]{"bb.unlocked","bb.coins","bb.gun","bb.blade","bb.shield","bb.slow","bb.multicatch"})PlayerPrefs.DeleteKey(k);unlocked=1;coins=0;gunLevel=1;bladeLevel=shieldLevel=slowLevel=0;multiCatch=false;Save();Show(Page.Settings);}

    private void Help()
    {
        Background("BounceBlade/Images/Backgrounds & Environments/Gameplay Background",.72f);Button("← BACK",uiRoot,new Vector2(-550,306),new Vector2(145,50),()=>Show(Page.Menu),Color.gray);Header("HOW TO PLAY","Guide the blade, protect the left wall, save the Grid");
        string[] lines={"1. The blade launches automatically from the left.","2. Drag vertically to move the shield along the left wall.","3. Blade hits pass through targets and remove one-third of target health.","4. Mini-guns aim automatically and also deal one-third target health per shot.","5. Catch the returning blade; three misses end the run.","6. Destroy all enemies and crates, then catch the blade to clear a stage.","7. Hold two fingers to slow time while the meter lasts.","8. Collect power-ups, use portals and switches, and avoid active hazards.","9. Earn shards and stars; upgrade equipment and clear all 100 stages."};
        for(int i=0;i<lines.Length;i++)Text(lines[i],uiRoot,new Vector2(0,150-i*44),new Vector2(1120,32),16,TextAnchor.MiddleCenter,i==3?Accent:new Color(.86f,.92f,.96f));
        Button("LET'S PLAY",uiRoot,new Vector2(0,-284),new Vector2(290,50),()=>StartStage(unlocked),Accent,true);
    }
    private void Story()
    {
        Background("BounceBlade/Images/Backgrounds & Environments/Victory Background",.72f);Button("← BACK",uiRoot,new Vector2(-550,306),new Vector2(145,50),()=>Show(Page.Menu),Color.gray);Header("THE STORY","Catch the light. Save the Grid. Become the last Guardian.");
        string s="In the beginning, there was only the Lumen Grid, a shimmering tapestry of light and data. At its heart burned the Golden Core, the first spark of creation. To protect it, the Guardians were forged, each wielding a shield of pure intent.\n\nFrom the void came the Corruption, twisting the Grid's energy into ravenous enemies. The Guardians created the Bounce Blade, a shard of the Core designed to slice through corruption and return to its master. But the Corruption poisoned its momentum.\n\nYou are the last Guardian. Across ten worlds—from the Neon Grid and Crimson Circuit to the Quantum Abyss, Storm Citadel and Celestial Forge—you must destroy the Corruption and catch the Blade. If the Blade slips past your shield, the darkness wins.";
        Text(s,uiRoot,new Vector2(0,4),new Vector2(980,390),20);Button("BEGIN CAMPAIGN",uiRoot,new Vector2(0,-245),new Vector2(340,58),()=>StartStage(unlocked),Accent,true);
    }
    private void Hud()
    {
        RectTransform root=Rect("HUD",uiRoot);Stretch(root);hudStage=Text("",root,new Vector2(-465,315),new Vector2(400,40),21,TextAnchor.MiddleLeft,Accent);
        hudScore=Text("",root,new Vector2(-465,277),new Vector2(410,32),18,TextAnchor.MiddleLeft,Color.white);hudLives=Text("",root,new Vector2(0,315),new Vector2(310,38),21);
        hudCombo=Text("",root,new Vector2(415,315),new Vector2(280,34),19,TextAnchor.MiddleRight,WorldColor[4]);hudPower=Text("",root,new Vector2(0,-320),new Vector2(650,32),17,TextAnchor.MiddleCenter,WorldColor[4]);
        hudSlow=Text("",root,new Vector2(0,-285),new Vector2(430,25),14,TextAnchor.MiddleCenter,Accent);Button("Ⅱ",root,new Vector2(570,310),new Vector2(76,56),()=>Show(Page.Pause),Accent,true);UpdateHud();
    }
    private void PausePage()
    {
        Background("BounceBlade/Images/Backgrounds & Environments/Gameplay Background",.82f);Image("Pause panel",uiRoot,"BounceBlade/Images/UI Elements/UI Panel",Vector2.zero,new Vector2(620,440));
        Text("PAUSED",uiRoot,new Vector2(0,140),new Vector2(520,66),40,TextAnchor.MiddleCenter,Accent);Button("RESUME",uiRoot,new Vector2(0,58),new Vector2(350,60),()=>Show(Page.Game),Accent,true);
        Button("RETRY STAGE",uiRoot,new Vector2(0,-20),new Vector2(350,56),()=>StartStage(stage),WorldColor[4]);Button("SETTINGS",uiRoot,new Vector2(0,-95),new Vector2(350,56),()=>Show(Page.Settings),WorldColor[2]);Button("MAIN MENU",uiRoot,new Vector2(0,-170),new Vector2(350,56),()=>Show(Page.Menu),WorldColor[3]);
    }
    private void WinPage()
    {
        Background("BounceBlade/Images/Backgrounds & Environments/Victory Background",.42f);Image("Victory banner",uiRoot,"BounceBlade/Images/Banners & Text/Victory Banner",new Vector2(0,222),new Vector2(660,120),true);
        Text("STAGE "+stage+" CLEARED",uiRoot,new Vector2(0,144),new Vector2(850,42),29,TextAnchor.MiddleCenter,WorldColor[4]);Text(new string('★',stars[stage])+new string('☆',3-stars[stage]),uiRoot,new Vector2(0,75),new Vector2(500,68),46,TextAnchor.MiddleCenter,WorldColor[4]);
        Text("SCORE "+score+"   •   CATCHES "+catches+"   •   BEST COMBO x"+bestCombo,uiRoot,new Vector2(0,15),new Vector2(1050,38),19);
        if(stage<MaxStages)Button("NEXT STAGE",uiRoot,new Vector2(0,-105),new Vector2(350,60),()=>StartStage(stage+1),Accent,true);
        Button("REPLAY",uiRoot,new Vector2(-190,-192),new Vector2(260,52),()=>StartStage(stage),WorldColor[2]);Button("MAIN MENU",uiRoot,new Vector2(190,-192),new Vector2(260,52),()=>Show(Page.Menu),WorldColor[3]);
    }
    private void LosePage()
    {
        Background("BounceBlade/Images/Backgrounds & Environments/Game Over Background",.44f);Image("Game Over banner",uiRoot,"BounceBlade/Images/Banners & Text/Game Over Banner",new Vector2(0,195),new Vector2(620,118),true);
        Text("THE CORRUPTION SPREADS…",uiRoot,new Vector2(0,118),new Vector2(800,40),25,TextAnchor.MiddleCenter,WorldColor[3]);Text("STAGE "+stage+"   •   SCORE "+score+"   •   TARGETS LEFT "+CountTargets(),uiRoot,new Vector2(0,52),new Vector2(960,38),20);
        Button("RETRY STAGE",uiRoot,new Vector2(-190,-66),new Vector2(310,62),()=>StartStage(stage),Accent,true);Button("MAIN MENU",uiRoot,new Vector2(190,-66),new Vector2(310,62),()=>Show(Page.Menu),WorldColor[3]);Button("UPGRADES",uiRoot,new Vector2(0,-153),new Vector2(310,56),()=>Show(Page.Shop),WorldColor[4]);
    }
}
