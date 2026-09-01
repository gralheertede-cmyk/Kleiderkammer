package de.vereinskleidung.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root, content;
    private Api api;
    private ArrayList<JSONObject> members = new ArrayList<>();
    private ArrayList<JSONObject> items = new ArrayList<>();
    private ArrayList<JSONObject> loans = new ArrayList<>();
    private ArrayList<JSONObject> history = new ArrayList<>();
    private boolean onHome = false;
    private Runnable currentBackAction = null;

    private final int BLUE = Color.rgb(36,87,166);
    private final int DARK = Color.rgb(28,42,56);
    private final int BG = Color.rgb(244,246,248);
    private final int CARD = Color.WHITE;
    private final int MUTED = Color.rgb(102,115,128);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        api = new Api();
        showLogin();
    }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, int radius){
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private TextView text(String s, int size, int color){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private TextView section(String s){
        TextView t=text(s,19,DARK); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,dp(14),0,dp(8)); return t;
    }
    private Button button(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(16); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER); b.setBackground(rounded(BLUE,14)); b.setPadding(dp(14),dp(10),dp(14),dp(10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56)); p.setMargins(0,dp(7),0,dp(7)); b.setLayoutParams(p); return b;
    }
    private Button lightButton(String s){
        Button b=button(s); b.setTextColor(BLUE); b.setBackground(rounded(Color.rgb(231,238,248),14)); return b;
    }
    private EditText edit(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setTextSize(16); e.setSingleLine(true); e.setPadding(dp(14),0,dp(14),0); e.setBackground(rounded(Color.WHITE,12));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54)); p.setMargins(0,dp(6),0,dp(6)); e.setLayoutParams(p); return e;
    }
    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(14),dp(16),dp(14)); c.setBackground(rounded(CARD,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,dp(8)); c.setLayoutParams(p); return c;
    }

    private void base(String heading, boolean showBack){
        base(heading, showBack, this::showHome);
    }

    private void base(String heading, boolean showBack, Runnable backAction){
        onHome = !showBack && !heading.equals("Anmeldung");
        currentBackAction = showBack ? backAction : null;
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(14),dp(16),dp(16));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            root.setPadding(dp(16), top + dp(14), dp(16), dp(16));
            return insets;
        });
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
        if(showBack){
            Button back=lightButton("‹ Zurück");
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(125),dp(46)); bp.setMargins(0,0,0,dp(8)); back.setLayoutParams(bp);
            back.setOnClickListener(v->{ if(currentBackAction!=null) currentBackAction.run(); else showHome(); });
            header.addView(back);
        }
        TextView h=text(heading,27,DARK); h.setTypeface(Typeface.DEFAULT,Typeface.BOLD); h.setPadding(dp(2),0,0,dp(10)); header.addView(h);
        root.addView(header);
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0,0,0,dp(20)); sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); root.requestApplyInsets();
    }

    @Override public void onBackPressed(){
        if(currentBackAction!=null){ currentBackAction.run(); }
        else if(!onHome){ showHome(); }
        else { super.onBackPressed(); }
    }

    private void showLogin(){
        base("Anmeldung",false); onHome=false;
        LinearLayout c=card(); c.addView(text("Gemeinsamer Vereinszugang",18,DARK));
        TextView info=text("Die Daten werden zentral über Google Sheets synchronisiert.",14,MUTED); info.setPadding(0,dp(4),0,dp(8)); c.addView(info);
        EditText url=edit("Google-Apps-Script-URL");
        EditText pw=edit("Vereins-Passwort"); pw.setInputType(0x81);
        url.setText(getPreferences(0).getString("url",""));
        c.addView(url); c.addView(pw); Button login=button("Anmelden"); c.addView(login); content.addView(c);
        login.setOnClickListener(v->{
            api.url=url.getText().toString().trim(); api.password=pw.getText().toString();
            if(api.url.isEmpty()||api.password.isEmpty()){toast("URL und Passwort eingeben");return;}
            getPreferences(0).edit().putString("url",api.url).apply(); login.setEnabled(false);
            api.call("summary",new JSONObject(),r->runOnUiThread(()->{login.setEnabled(true); if(r.optBoolean("ok")) load(); else toast(r.optString("error","Anmeldung fehlgeschlagen"));}));
        });
    }

    private void load(){
        api.call("bootstrap",new JSONObject(),r->runOnUiThread(()->{
            if(!r.optBoolean("ok")){toast(r.optString("error","Daten konnten nicht geladen werden"));return;}
            members=toList(r.optJSONArray("members")); items=toList(r.optJSONArray("items")); loans=toList(r.optJSONArray("loans")); history=toList(r.optJSONArray("history")); showHome();
        }));
    }
    private ArrayList<JSONObject> toList(JSONArray a){ArrayList<JSONObject>x=new ArrayList<>(); if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)x.add(o);}return x;}

    private void showHome(){
        base("Kleiderkammer",false); onHome=true;
        TextView sub=text("Vereinskleidung einfach verwalten",16,MUTED); sub.setPadding(dp(2),0,0,dp(12)); content.addView(sub);
        Button a=button("＋  Kleidung ausgeben"); a.setOnClickListener(v->showIssue()); content.addView(a);
        Button b=button("↩  Kleidung zurücknehmen"); b.setOnClickListener(v->showReturn()); content.addView(b);
        Button c=button("👥  Wer hat was? / Archiv"); c.setOnClickListener(v->showOverview()); content.addView(c);
        Button d=button("📦  Bestand"); d.setOnClickListener(v->showStock()); content.addView(d);
        Button e=lightButton("⚙  Verwaltung / Inventur"); e.setOnClickListener(v->showAdmin()); content.addView(e);
    }

    private ArrayAdapter<String> memberAdapter(){ArrayList<String>a=new ArrayList<>();for(JSONObject m:members)a.add(m.optString("name"));return new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,a);}
    private Spinner styledSpinner(ArrayAdapter<String> adapter){ Spinner s=new Spinner(this); s.setAdapter(adapter); s.setBackground(rounded(Color.WHITE,12)); s.setPadding(dp(12),0,dp(12),0); s.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54))); return s; }

    private class SizePicker {
        LinearLayout view; String selected=""; EditText custom;
        String value(){ String c=custom.getText().toString().trim(); return c.isEmpty()?selected:c.toUpperCase(Locale.GERMANY); }
    }
    private SizePicker sizePicker(){
        SizePicker p=new SizePicker(); p.view=new LinearLayout(this); p.view.setOrientation(LinearLayout.VERTICAL);
        TextView lab=text("Größe auswählen",16,DARK); lab.setPadding(0,dp(6),0,dp(6)); p.view.addView(lab);
        HorizontalScrollView hs=new HorizontalScrollView(this); LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[] sizes={"122","128","134","140","146","152","158","164","170","XS","S","M","L","XL","XXL","3XL"}; ArrayList<Button> bs=new ArrayList<>();
        for(String z:sizes){ Button b=lightButton(z); LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(dp(68),dp(46));q.setMargins(0,0,dp(6),0);b.setLayoutParams(q);bs.add(b);row.addView(b);
            b.setOnClickListener(v->{p.selected=z;p.custom.setText("");for(Button x:bs){x.setTextColor(BLUE);x.setBackground(rounded(Color.rgb(231,238,248),12));}b.setTextColor(Color.WHITE);b.setBackground(rounded(BLUE,12));}); }
        hs.addView(row); p.view.addView(hs); p.custom=edit("Andere Größe (optional)"); p.custom.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(50))); p.view.addView(p.custom); return p;
    }

    private void showIssue(){
        base("Kleidung ausgeben",true);
        if(members.isEmpty()){content.addView(text("Bitte zuerst unter Verwaltung ein Mitglied anlegen.",16,MUTED));return;}
        LinearLayout c=card(); c.addView(section("Person")); Spinner sp=styledSpinner(memberAdapter()); c.addView(sp);
        c.addView(section("Kleidungsstück")); String[] types={"T-Shirt","Trikot","Trikothose","Trainingsjacke","Trainingshose"};
        Spinner typ=styledSpinner(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types)); c.addView(typ);
        SizePicker size=sizePicker(); c.addView(size.view); EditText number=edit("Nummer (nur falls vorhanden)"); c.addView(number);
        Button save=button("Ausgabe speichern"); c.addView(save); content.addView(c);
        save.setOnClickListener(v->{ String sz=size.value(); if(sz.isEmpty()){toast("Bitte Größe auswählen");return;} JSONObject m=members.get(sp.getSelectedItemPosition()); JSONObject p=new JSONObject(); try{p.put("memberId",m.optString("id"));p.put("type",typ.getSelectedItem().toString());p.put("size",sz);p.put("number",number.getText().toString().trim());}catch(Exception ignored){}
            save.setEnabled(false); api.call("issue",p,r->runOnUiThread(()->{save.setEnabled(true);if(r.optBoolean("ok")){toast("Ausgabe gespeichert");load();}else toast(r.optString("error","Nicht möglich"));})); });
    }

    private void showReturn(){
        base("Rückgabe",true);
        if(members.isEmpty()){content.addView(text("Noch keine Mitglieder vorhanden.",16,MUTED));return;}
        LinearLayout c=card(); c.addView(section("Person auswählen")); Spinner sp=styledSpinner(memberAdapter()); c.addView(sp); Button find=lightButton("Ausgeliehene Sachen anzeigen"); c.addView(find); content.addView(c);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
        find.setOnClickListener(v->{ list.removeAllViews(); String id=members.get(sp.getSelectedItemPosition()).optString("id"); int count=0;
            for(JSONObject l:loans) if(l.optString("memberId").equals(id)){ CheckBox cb=new CheckBox(this); cb.setText(itemText(l)); cb.setTextSize(16); cb.setPadding(dp(8),dp(8),dp(8),dp(8)); cb.setTag(l.optString("id")); list.addView(cb); count++; }
            if(count==0){list.addView(text("Diese Person hat aktuell nichts ausgeliehen.",16,MUTED));return;}
            Button all=lightButton("Alle markieren"); list.addView(all); all.setOnClickListener(x->{for(int i=0;i<list.getChildCount();i++){View q=list.getChildAt(i);if(q instanceof CheckBox)((CheckBox)q).setChecked(true);}});
            Button ret=button("Markierte zurückgeben"); list.addView(ret); ret.setOnClickListener(x->{JSONArray ids=new JSONArray();for(int i=0;i<list.getChildCount();i++){View q=list.getChildAt(i);if(q instanceof CheckBox&&((CheckBox)q).isChecked())ids.put(q.getTag().toString());}if(ids.length()==0){toast("Bitte mindestens ein Teil markieren");return;}JSONObject p=new JSONObject();try{p.put("itemIds",ids);}catch(Exception ignored){}ret.setEnabled(false);api.call("return",p,r->runOnUiThread(()->{ret.setEnabled(true);if(r.optBoolean("ok")){toast("Rückgabe gespeichert");load();}else toast(r.optString("error"));}));});
        });
    }

    private void showOverview(){
        base("Wer hat was? / Archiv",true);
        if(members.isEmpty()){content.addView(text("Noch keine Mitglieder vorhanden.",16,MUTED));return;}

        TextView info=text("Übersicht aller Personen. Angezeigt werden die zuletzt bekannten Größen je Kleidungsart. Für das vollständige Archiv eine Person öffnen.",14,MUTED);
        info.setPadding(dp(2),0,0,dp(10));
        content.addView(info);

        for(JSONObject m:members){
            final JSONObject member=m;
            String id=m.optString("id");
            LinearLayout c=card();

            TextView name=text(m.optString("name"),20,DARK);
            name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            c.addView(name);

            LinkedHashMap<String,String> sizes=latestSizesForMember(id);
            if(sizes.isEmpty()){
                TextView none=text("Noch keine Größen hinterlegt",15,MUTED);
                none.setPadding(0,dp(8),0,dp(4));
                c.addView(none);
            } else {
                for(String type:clothingTypes()){
                    String size=sizes.get(type);
                    if(size!=null && !size.isEmpty()){
                        TextView row=text(type+": "+size,16,DARK);
                        row.setPadding(dp(4),dp(3),0,dp(3));
                        c.addView(row);
                    }
                }
            }

            ArrayList<String> current=currentLoansForMember(id);
            TextView curTitle=text("Aktuell ausgeliehen",14,BLUE);
            curTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            curTitle.setPadding(0,dp(10),0,dp(4));
            c.addView(curTitle);
            if(current.isEmpty()){
                c.addView(text("Nichts ausgeliehen",15,MUTED));
            } else {
                for(String line:current){
                    TextView t=text("• "+line,15,DARK);
                    t.setPadding(dp(4),dp(2),0,dp(2));
                    c.addView(t);
                }
            }

            Button open=lightButton("Person öffnen / gesamtes Archiv");
            open.setOnClickListener(v->showPersonArchive(member));
            c.addView(open);
            content.addView(c);
        }
    }

    private String[] clothingTypes(){
        return new String[]{"T-Shirt","Trikot","Trikothose","Trainingsjacke","Trainingshose"};
    }

    private LinkedHashMap<String,String> latestSizesForMember(String memberId){
        LinkedHashMap<String,String> result=new LinkedHashMap<>();

        // Aktuell ausgeliehene Sachen haben Vorrang.
        for(JSONObject l:loans){
            if(l.optString("memberId").equals(memberId)){
                String type=l.optString("type");
                String size=l.optString("size");
                if(!type.isEmpty() && !size.isEmpty()) result.put(type,size);
            }
        }

        // history kommt vom Server bereits mit den neuesten Vorgängen zuerst.
        for(JSONObject h:history){
            if(!h.optString("memberId").equals(memberId)) continue;
            String type=h.optString("type");
            String size=h.optString("size");
            if(type.isEmpty() || size.isEmpty()) continue;
            if(!result.containsKey(type)) result.put(type,size);
        }
        return result;
    }

    private ArrayList<String> currentLoansForMember(String memberId){
        ArrayList<String> result=new ArrayList<>();
        for(JSONObject l:loans){
            if(l.optString("memberId").equals(memberId)) result.add(itemText(l));
        }
        return result;
    }

    private void showPersonArchive(JSONObject member){
        final String memberId=member.optString("id");
        final String memberName=member.optString("name");
        base("Archiv – "+memberName,true,this::showOverview);

        LinearLayout sizesCard=card();
        sizesCard.addView(section("Größen nach Kleidungsart"));
        LinkedHashMap<String,String> sizes=latestSizesForMember(memberId);
        boolean hasSize=false;
        for(String type:clothingTypes()){
            String size=sizes.get(type);
            if(size!=null && !size.isEmpty()){
                TextView t=text(type+": "+size,17,DARK);
                t.setPadding(dp(4),dp(4),0,dp(4));
                sizesCard.addView(t);
                hasSize=true;
            }
        }
        if(!hasSize) sizesCard.addView(text("Noch keine Größen hinterlegt.",15,MUTED));
        content.addView(sizesCard);

        LinearLayout currentCard=card();
        currentCard.addView(section("Aktuell ausgeliehen"));
        ArrayList<String> current=currentLoansForMember(memberId);
        if(current.isEmpty()){
            currentCard.addView(text("Nichts ausgeliehen",15,MUTED));
        } else {
            for(String line:current){
                TextView t=text("• "+line,16,DARK);
                t.setPadding(dp(4),dp(3),0,dp(3));
                currentCard.addView(t);
            }
        }
        content.addView(currentCard);

        LinearLayout archiveCard=card();
        archiveCard.addView(section("Gesamtes Archiv"));
        boolean any=false;
        for(JSONObject h:history){
            if(!h.optString("memberId").equals(memberId)) continue;

            String action=h.optString("action");
            String actionText=action.equals("ISSUE") ? "Ausgabe" :
                    action.equals("RETURN") ? "Rückgabe" : action;

            StringBuilder line=new StringBuilder();
            line.append(dateShort(h.optString("timestamp")))
                .append(" · ")
                .append(actionText)
                .append(" · ")
                .append(h.optString("type"))
                .append(" ")
                .append(h.optString("size"));

            if(!h.optString("number").isEmpty()){
                line.append(" · Nr. ").append(h.optString("number"));
            }

            TextView t=text(line.toString(),15,DARK);
            t.setPadding(dp(4),dp(4),0,dp(4));
            archiveCard.addView(t);
            any=true;
        }
        if(!any) archiveCard.addView(text("Noch keine Vorgänge im Archiv.",15,MUTED));
        content.addView(archiveCard);
    }

    private String itemText(JSONObject i){return i.optString("type")+" · "+i.optString("size")+(i.optString("number").isEmpty()?"":" · Nr. "+i.optString("number"));}
    private String dateShort(String iso){try{Date d=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).parse(iso);return new SimpleDateFormat("dd.MM.yyyy",Locale.GERMANY).format(d);}catch(Exception e){return iso.length()>=10?iso.substring(0,10):iso;}}

    private void showStock(){
        base("Bestand",true);

        TreeMap<String,Integer> available=new TreeMap<>();
        for(JSONObject i:items){
            if(!i.optString("status").equals("available")) continue;
            String type=i.optString("type");
            String size=i.optString("size");
            String key=type+" | Größe "+size;
            available.put(key,available.getOrDefault(key,0)+1);
        }

        TextView info=text("Nur aktuell verfügbare, nicht verliehene Kleidung. Nummern werden hier bewusst nicht angezeigt.",14,MUTED);
        info.setPadding(dp(2),0,0,dp(10));
        content.addView(info);

        if(available.isEmpty()){
            content.addView(text("Aktuell ist kein verfügbarer Bestand vorhanden.",16,MUTED));
        } else {
            for(Map.Entry<String,Integer> entry:available.entrySet()){
                LinearLayout c=card();
                TextView t=text(entry.getKey(),17,DARK);
                t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                c.addView(t);
                c.addView(text("Verfügbar: "+entry.getValue(),16,MUTED));
                content.addView(c);
            }
        }

        Button add=button("＋ Bestand ergänzen");
        add.setOnClickListener(v->showAddStock());
        content.addView(add);
    }

    private void showAddStock(){
        base("Bestand ergänzen",true); LinearLayout c=card(); String[] types={"T-Shirt","Trikot","Trikothose","Trainingsjacke","Trainingshose"};
        c.addView(section("Kleidungsart")); Spinner typ=styledSpinner(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types)); c.addView(typ); SizePicker size=sizePicker(); c.addView(size.view);
        EditText nums=edit("Nummern, z. B. 10-19 oder 10,11,12"); c.addView(nums); EditText count=edit("Anzahl (bei Sachen ohne Nummer)"); count.setInputType(2); c.addView(count); Button b=button("In Bestand aufnehmen");c.addView(b);content.addView(c);
        b.setOnClickListener(v->{String sz=size.value();if(sz.isEmpty()){toast("Bitte Größe auswählen");return;}JSONObject p=new JSONObject();try{p.put("type",typ.getSelectedItem().toString());p.put("size",sz);p.put("numbers",nums.getText().toString().trim());p.put("count",count.getText().toString().trim());}catch(Exception ignored){}b.setEnabled(false);api.call("addStock",p,r->runOnUiThread(()->{b.setEnabled(true);if(r.optBoolean("ok")){toast("Bestand ergänzt");load();}else toast(r.optString("error"));}));});
    }

    private void showAdmin(){
        base("Verwaltung / Inventur",true); LinearLayout c=card(); c.addView(section("Neues Mitglied")); EditText name=edit("Vor- und Nachname"); c.addView(name); Button add=button("Mitglied hinzufügen"); c.addView(add); content.addView(c);
        add.setOnClickListener(v->{if(name.getText().toString().trim().isEmpty()){toast("Bitte Namen eingeben");return;}JSONObject p=new JSONObject();try{p.put("name",name.getText().toString().trim());}catch(Exception ignored){}api.call("addMember",p,r->runOnUiThread(()->{if(r.optBoolean("ok")){toast("Mitglied hinzugefügt");load();}else toast(r.optString("error"));}));});
        content.addView(section("Mitglieder")); for(JSONObject m:members){LinearLayout mc=card();mc.addView(text(m.optString("name"),16,DARK));content.addView(mc);} Button refresh=lightButton("↻ Daten aktualisieren");refresh.setOnClickListener(v->load());content.addView(refresh);
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private static class Api {
        String url="",password="";
        interface CB{void ok(JSONObject r);}
        void call(String action, JSONObject data, CB cb){ new Thread(()->{
            try{data.put("action",action);data.put("password",password); HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setInstanceFollowRedirects(true); c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");
                try(OutputStream o=c.getOutputStream()){o.write(data.toString().getBytes("UTF-8"));}
                int code=c.getResponseCode(); InputStream in=code<400?c.getInputStream():c.getErrorStream(); BufferedReader br=new BufferedReader(new InputStreamReader(in));StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line);String body=s.toString();
                if(body.trim().startsWith("<")){JSONObject r=new JSONObject();r.put("ok",false);r.put("error","Google-Verbindung liefert keine gültigen Daten. Bitte Apps-Script-Bereitstellung prüfen.");cb.ok(r);return;}cb.ok(new JSONObject(body));
            }catch(Exception e){try{JSONObject r=new JSONObject();r.put("ok",false);r.put("error","Netzwerkfehler: "+e.getMessage());cb.ok(r);}catch(Exception ignored){}}
        }).start(); }
    }
}
