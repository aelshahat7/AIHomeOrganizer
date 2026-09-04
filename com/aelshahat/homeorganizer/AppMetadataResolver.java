package com.aelshahat.homeorganizer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.UserHandle;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Conservative resolver: a weak label match is never promoted to a real package. */
public final class AppMetadataResolver {
    public static final class Metadata {
        public final String packageName,label; public final int applicationCategory; public final boolean exactLabel,unique;
        Metadata(String p,String l,int c,boolean e,boolean u){packageName=p;label=l;applicationCategory=c;exactLabel=e;unique=u;}
    }
    private final Context context; private final LauncherApps launcherApps;
    public AppMetadataResolver(Context context){this.context=context.getApplicationContext();this.launcherApps=(LauncherApps)this.context.getSystemService(Context.LAUNCHER_APPS_SERVICE);}
    public Metadata resolve(String homeLabel){
        String wanted=normalize(homeLabel);if(wanted.isEmpty()||launcherApps==null)return null;
        List<LauncherActivityInfo> activities;
        try{UserHandle user=Process.myUserHandle();activities=launcherApps.getActivityList(null,user);}catch(Throwable ignored){return null;}
        List<LauncherActivityInfo> exact=new ArrayList<>();
        for(LauncherActivityInfo info:activities){if(info==null||info.getApplicationInfo()==null)continue;if(wanted.equals(normalize(String.valueOf(info.getLabel()))))exact.add(info);}
        if(exact.size()==1){LauncherActivityInfo i=exact.get(0);return metadata(i,true,true);}
        if(exact.size()>1)return null;
        LauncherActivityInfo packageMatch=null;int matches=0;
        for(LauncherActivityInfo info:activities){if(info==null)continue;String pkg=normalize(info.getComponentName().getPackageName());if(wanted.length()>=6&&pkg.equals(wanted)){packageMatch=info;matches++;}else if(wanted.length()>=6&&pkg.endsWith("."+wanted)){packageMatch=info;matches++;}}
        if(matches==1)return metadata(packageMatch,false,true);
        // Substring/fuzzy matches are intentionally rejected. Accessibility only gives the launcher node,
        // so guessing a package from a short or partial label can silently misclassify a real app.
        return null;
    }
    private Metadata metadata(LauncherActivityInfo i,boolean exact,boolean unique){ApplicationInfo ai=i.getApplicationInfo();return new Metadata(i.getComponentName().getPackageName(),String.valueOf(i.getLabel()),ai.category,exact,unique);}
    public static String normalize(String value){if(value==null)return "";String s=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace("\u200b","").replace("\u200c","").replace("\u200d","").replace("\ufeff","").replaceAll("[\\p{M}]","").replaceAll("[^\\p{L}\\p{N}]+"," ").trim();return s.replaceAll("\\s+"," ");}
}
