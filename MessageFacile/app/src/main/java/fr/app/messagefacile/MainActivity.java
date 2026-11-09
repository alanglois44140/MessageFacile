package fr.app.messagefacile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

/** Application locale de gestion de contacts et d'envoi de SMS. */
public class MainActivity extends Activity {
    private static final int REQ_SMS = 41, REQ_CSV = 42, REQ_LOGO = 43;
    private SharedPreferences prefs;
    private final ArrayList<Contact> contacts = new ArrayList<>();
    private final ArrayList<Template> templates = new ArrayList<>();
    private final ArrayList<HistoryEntry> history = new ArrayList<>();
    private final ArrayList<Integer> selected = new ArrayList<>();
    private LinearLayout root, content, header;
    private EditText messagePreview;
    private Spinner templateSpinner;
    private TextView countLabel, logoLabel;
    private CheckBox selectAllCheck;
    private boolean syncingSelectAll = false;
    private int accent = Color.rgb(39,110,241);
    private String appName = "MessageFacile", logoText = "✉";
    private Uri logoUri;
    private int currentTab = 0;
    private String pendingSms = null;

    static class Contact {
        String first, last, phone;
        Contact(String f, String l, String p) { first=f; last=l; phone=p; }
        String display() { return (first+" "+last).trim(); }
    }
    static class Template {
        String name, body;
        Template(String n,String b){name=n;body=b;}
    }
    static class HistoryEntry {
        long date;
        String recipient, phone, message, status;
        HistoryEntry(long d,String r,String p,String m,String s){date=d;recipient=r;phone=p;message=m;status=s;}
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("message_facile",MODE_PRIVATE);
        loadData();
        render();
    }

    private void loadData() {
        appName=prefs.getString("appName","MessageFacile");
        logoText=prefs.getString("logoText","✉");
        accent=prefs.getInt("accent",Color.rgb(39,110,241));
        String lu=prefs.getString("logoUri",null); if(lu!=null) try{logoUri=Uri.parse(lu);}catch(Exception ignored){}
        try {
            JSONArray a=new JSONArray(prefs.getString("contacts","[]"));
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);contacts.add(new Contact(o.optString("first"),o.optString("last"),o.optString("phone")));}
        } catch(Exception ignored){}
        try {
            JSONArray h=new JSONArray(prefs.getString("history","[]"));
            for(int i=0;i<h.length();i++){
                JSONObject o=h.getJSONObject(i);
                history.add(new HistoryEntry(o.optLong("date"),o.optString("recipient"),o.optString("phone"),o.optString("message"),o.optString("status")));
            }
        } catch(Exception ignored){}
        try {
            JSONArray a=new JSONArray(prefs.getString("templates","[]"));
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);templates.add(new Template(o.optString("name"),o.optString("body")));}
        } catch(Exception ignored){}
        if(templates.isEmpty()){
            templates.add(new Template("Rappel", "Bonjour {prenom}, nous vous rappelons que ... Bonne journée."));
            templates.add(new Template("Information", "Bonjour {prenom}, nous souhaitons vous informer que ..."));
            saveData();
        }
    }
    private void saveData(){
        try{
            JSONArray c=new JSONArray(); for(Contact x:contacts){JSONObject o=new JSONObject();o.put("first",x.first);o.put("last",x.last);o.put("phone",x.phone);c.put(o);}
            JSONArray t=new JSONArray(); for(Template x:templates){JSONObject o=new JSONObject();o.put("name",x.name);o.put("body",x.body);t.put(o);}
            JSONArray h=new JSONArray();
            for(HistoryEntry x:history){
                JSONObject o=new JSONObject();o.put("date",x.date);o.put("recipient",x.recipient);o.put("phone",x.phone);o.put("message",x.message);o.put("status",x.status);h.put(o);
            }
            prefs.edit().putString("contacts",c.toString()).putString("templates",t.toString()).putString("history",h.toString()).putString("appName",appName).putString("logoText",logoText).putInt("accent",accent).putString("logoUri",logoUri==null?null:logoUri.toString()).apply();
        }catch(Exception ignored){}
    }

    private void render(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,248,252));
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(14),dp(18),dp(14));header.setBackgroundColor(Color.WHITE);
        if(logoUri!=null){ ImageView image=new ImageView(this); image.setImageURI(logoUri); image.setScaleType(ImageView.ScaleType.CENTER_CROP); header.addView(image,new LinearLayout.LayoutParams(dp(42),dp(42))); }
        else { logoLabel=new TextView(this);logoLabel.setText(logoText);logoLabel.setTextSize(27);logoLabel.setTextColor(accent);header.addView(logoLabel,new LinearLayout.LayoutParams(dp(42),dp(42))); }
        TextView title=new TextView(this);title.setText(appName);title.setTextSize(21);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Color.rgb(28,39,56));header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(header);
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(Color.WHITE);
        String[] tabs={"Contacts","Modèles","Apparence","Historique"};
        for(int i=0;i<tabs.length;i++){final int tab=i;Button b=button(tabs[i]);b.setTextSize(11);b.setPadding(dp(2),0,dp(2),0);b.setOnClickListener(v->{currentTab=tab;render();});nav.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));}
        root.addView(nav);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(16),dp(16),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        if(currentTab==0) showContacts(); else if(currentTab==1) showTemplates(); else if(currentTab==2) showAppearance(); else showHistory();
    }

    private void showContacts(){
        section("Mes contacts");
        LinearLayout actions=row();Button add=button("+ Ajouter");add.setOnClickListener(v->contactDialog(-1));Button csv=button("Importer CSV");csv.setOnClickListener(v->pickCsv());actions.addView(add,new LinearLayout.LayoutParams(0,dp(46),1));actions.addView(csv,new LinearLayout.LayoutParams(0,dp(46),1));content.addView(actions);
        countLabel=label(contacts.size()+" contact(s) enregistré(s)",14,Color.GRAY);content.addView(countLabel);
        selectAllCheck = new CheckBox(this);
        selectAllCheck.setText("Tout sélectionner / désélectionner");
        selectAllCheck.setTextSize(14);
        selectAllCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
        selectAllCheck.setChecked(!contacts.isEmpty() && selected.size() == contacts.size());
        selectAllCheck.setOnCheckedChangeListener((v, checked) -> {
            if (syncingSelectAll) return;
            selected.clear();
            if (checked) for (int n = 0; n < contacts.size(); n++) selected.add(n);
            render();
        });
        content.addView(selectAllCheck);
        for(int i=0;i<contacts.size();i++){
            final int index=i;Contact c=contacts.get(i);LinearLayout card=card();CheckBox check=new CheckBox(this);check.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));check.setText(c.display()+"\n"+c.phone);check.setTextSize(15);check.setPadding(dp(6),dp(8),dp(6),dp(8));check.setChecked(selected.contains(i));check.setOnCheckedChangeListener((v,on)->{if(on){if(!selected.contains(index))selected.add(index);}else selected.remove((Integer)index);if(selectAllCheck!=null){syncingSelectAll=true;selectAllCheck.setChecked(!contacts.isEmpty() && selected.size()==contacts.size());syncingSelectAll=false;}});card.addView(check,new LinearLayout.LayoutParams(0,-2,1));Button edit=smallButton("✎");edit.setOnClickListener(v->contactDialog(index));Button del=smallButton("×");del.setOnClickListener(v->{new AlertDialog.Builder(this).setMessage("Supprimer ce contact ?").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{contacts.remove(index);selected.clear();saveData();render();}).show();});card.addView(edit);card.addView(del);
        }
        section("Préparer un envoi");
        ArrayList<String> names=new ArrayList<>();for(Template t:templates)names.add(t.name);if(names.isEmpty())names.add("Aucun modèle");
        templateSpinner=new Spinner(this);templateSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));content.addView(templateSpinner);
        messagePreview=new EditText(this);messagePreview.setMinLines(4);messagePreview.setGravity(Gravity.TOP);messagePreview.setTextSize(15);messagePreview.setHint("Le contenu du SMS apparaîtra ici…");messagePreview.setBackground(roundBg(Color.WHITE,Color.rgb(220,226,235)));content.addView(messagePreview,new LinearLayout.LayoutParams(-1,-2));
        templateSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(pos<templates.size())messagePreview.setText(templates.get(pos).body);}});
        Button preview=button("Actualiser avec les prénoms");preview.setOnClickListener(v->showPreview());content.addView(preview);
        Button send=button("Envoyer le SMS aux contacts sélectionnés");send.setTextColor(Color.WHITE);send.setBackground(rippleBg(accent,accent));send.setOnClickListener(v->confirmSend());content.addView(send);
        TextView note=label("Les SMS sont envoyés séparément à chaque numéro. Vérifie les destinataires et le contenu avant validation.",12,Color.DKGRAY);content.addView(note);
    }

    private void showPreview(){
        if(selected.isEmpty()){toast("Sélectionne au moins un contact.");return;}
        String base=messagePreview.getText().toString();if(base.trim().isEmpty())return;
        StringBuilder s=new StringBuilder();for(int idx:selected){if(idx>=contacts.size())continue;Contact c=contacts.get(idx);s.append(c.display()).append(" : ").append(personalize(base,c)).append("\n\n");}
        new AlertDialog.Builder(this).setTitle("Aperçu des messages ("+selected.size()+")").setMessage(s.toString()).setPositiveButton("Fermer",null).show();
    }
    private String personalize(String body,Contact c){return body.replace("{prenom}",c.first).replace("{nom}",c.last).replace("{nom_complet}",c.display());}
    private void confirmSend(){
        if(selected.isEmpty()){toast("Sélectionne au moins un contact.");return;}
        String body=messagePreview.getText().toString().trim();
        if(body.isEmpty()){toast("Le message est vide.");return;}
        new AlertDialog.Builder(this)
                .setTitle("Confirmation d'envoi")
                .setMessage("Êtes-vous sûr de vouloir envoyer votre message ?")
                .setNegativeButton("Non", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Oui", (dialog, which) -> sendNow(body))
                .show();
    }
    private void sendNow(String body){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){pendingSms=body;requestPermissions(new String[]{Manifest.permission.SEND_SMS},REQ_SMS);return;}
        SmsManager manager=SmsManager.getDefault();
        int sent=0, failed=0;
        for(int idx:new ArrayList<>(selected)){
            if(idx<0||idx>=contacts.size())continue;
            Contact c=contacts.get(idx);
            String phone=c.phone.replaceAll("[\\s().-]", "");
            String text=personalize(body,c);
            if(phone.length()<6){
                addHistory(c.display(),c.phone,text,"Échec : numéro de téléphone invalide");
                failed++;
                continue;
            }
            try{
                ArrayList<String> parts=manager.divideMessage(text);
                if(parts.size()>1)manager.sendMultipartTextMessage(phone,null,parts,null,null);
                else manager.sendTextMessage(phone,null,text,null,null);
                addHistory(c.display(),phone,text,"Transmis à Android — réception non confirmée");
                sent++;
            }catch(SecurityException e){
                addHistory(c.display(),phone,text,"Échec : autorisation SMS indisponible");
                failed++;
            }catch(Exception e){
                addHistory(c.display(),phone,text,"Échec : "+(e.getMessage()==null?"erreur lors de l'envoi":e.getMessage()));
                failed++;
            }
        }
        saveData();
        toast(sent+" SMS confié(s) à Android"+(failed>0 ? " — "+failed+" échec(s)" : "")+". Consulte l'historique pour le détail.");
    }
    private void addHistory(String recipient,String phone,String message,String status){
        history.add(0,new HistoryEntry(System.currentTimeMillis(),recipient,phone,message,status));
    }
    private void showHistory(){
        section("Historique des envois");
        content.addView(label("Les éléments sont conservés uniquement sur ce téléphone. « Transmis à Android » ne signifie pas que le SMS a été reçu.",12,Color.DKGRAY));
        if(history.isEmpty()){
            content.addView(label("Aucun envoi enregistré pour le moment.",15,Color.GRAY));
            return;
        }
        Button clear=button("Effacer tout l'historique");
        clear.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Effacer l'historique ?")
                .setMessage("Cette suppression est définitive.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Effacer",(d,w)->{history.clear();saveData();render();})
                .show());
        content.addView(clear);
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("dd/MM/yyyy à HH:mm",Locale.FRANCE);
        for(int i=0;i<history.size();i++){
            final int index=i;
            HistoryEntry entry=history.get(i);
            LinearLayout item=card();
            LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);
            TextView who=label(entry.recipient+" — "+entry.phone,15,Color.rgb(30,42,60));who.setTypeface(null,Typeface.BOLD);
            TextView when=label(format.format(new java.util.Date(entry.date)),12,Color.GRAY);
            TextView status=label(entry.status,13,entry.status.startsWith("Échec")?Color.rgb(190,45,45):Color.rgb(22,125,85));
            details.addView(who);details.addView(when);details.addView(status);
            item.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            Button view=smallButton("Voir");
            view.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(entry.recipient+" — "+entry.phone).setMessage("Date : "+format.format(new java.util.Date(entry.date))+"\n\nStatut : "+entry.status+"\n\nMessage :\n"+entry.message).setPositiveButton("Fermer",null).setNeutralButton("Supprimer",(d,w)->{history.remove(index);saveData();render();}).show());
            item.addView(view);
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==REQ_SMS){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){String message=pendingSms;pendingSms=null;if(message!=null)sendNow(message);}else{String message=pendingSms;pendingSms=null;if(message!=null){for(int idx:new ArrayList<>(selected)){if(idx>=0&&idx<contacts.size()){Contact c=contacts.get(idx);addHistory(c.display(),c.phone,personalize(message,c),"Échec : autorisation SMS refusée");}}saveData();}toast("Autorisation SMS nécessaire pour l'envoi.");}}}

    private void showTemplates(){
        section("Bibliothèque de modèles");Button add=button("+ Créer un modèle");add.setOnClickListener(v->templateDialog(-1));content.addView(add);
        for(int i=0;i<templates.size();i++){final int idx=i;Template t=templates.get(i);LinearLayout c=card();LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);TextView n=label(t.name,16,Color.rgb(30,42,60));n.setTypeface(null,Typeface.BOLD);TextView b=label(t.body,13,Color.DKGRAY);col.addView(n);col.addView(b);c.addView(col,new LinearLayout.LayoutParams(0,-2,1));Button e=smallButton("✎");e.setOnClickListener(v->templateDialog(idx));Button x=smallButton("×");x.setOnClickListener(v->{templates.remove(idx);saveData();render();});c.addView(e);c.addView(x);}
        section("Variables disponibles");content.addView(label("{prenom}  •  {nom}  •  {nom_complet}",14,Color.DKGRAY));
    }
    private void showAppearance(){
        section("Identité de l'application");EditText name=field("Nom de l'application",appName);content.addView(name);EditText logo=field("Logo (emoji ou symbole)",logoText);content.addView(logo);
        Button chooseLogo=button("Choisir une image comme logo");chooseLogo.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_LOGO);});content.addView(chooseLogo);
        section("Couleur principale");String[] colors={"Bleu","Vert","Prune","Orange","Noir"};int[] values={Color.rgb(39,110,241),Color.rgb(22,145,105),Color.rgb(126,76,170),Color.rgb(226,125,45),Color.rgb(45,52,64)};
        for(int i=0;i<colors.length;i++){final int k=i;Button b=button("●  "+colors[i]);b.setTextColor(values[i]);b.setOnClickListener(v->{accent=values[k];saveData();render();});content.addView(b);}
        EditText customColor=field("Couleur personnalisée au format #RRGGBB",String.format(java.util.Locale.US,"#%06X",(0xFFFFFF & accent)));content.addView(customColor);
        Button applyColor=button("Appliquer ma couleur");applyColor.setOnClickListener(v->{try{String value=customColor.getText().toString().trim();if(!value.startsWith("#"))value="#"+value;accent=Color.parseColor(value);saveData();render();}catch(Exception ex){toast("Format attendu : #RRGGBB (ex. #2457A6).");}});content.addView(applyColor);
        Button save=button("Enregistrer le nom et le logo");save.setOnClickListener(v->{appName=name.getText().toString().trim().isEmpty()?"MessageFacile":name.getText().toString().trim();logoText=logo.getText().toString().trim().isEmpty()?"✉":logo.getText().toString().trim();saveData();render();});content.addView(save);
        content.addView(label("Les données restent sur ce téléphone. Pense à protéger l'appareil et à ne pas importer de fichiers contenant des contacts sans base légitime.",12,Color.DKGRAY));
    }

    private void contactDialog(int index){boolean edit=index>=0;Contact old=edit?contacts.get(index):new Contact("","","");LinearLayout box=new LinearLayout(this);box.setPadding(dp(16),dp(8),dp(16),0);box.setOrientation(LinearLayout.VERTICAL);EditText f=field("Prénom",old.first),l=field("Nom",old.last),p=field("Téléphone portable",old.phone);box.addView(f);box.addView(l);box.addView(p);new AlertDialog.Builder(this).setTitle(edit?"Modifier le contact":"Nouveau contact").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{String phone=p.getText().toString().trim();if(phone.isEmpty()){toast("Le numéro de téléphone est obligatoire.");return;}Contact c=new Contact(f.getText().toString().trim(),l.getText().toString().trim(),phone);if(edit)contacts.set(index,c);else contacts.add(c);saveData();render();}).show();}
    private void templateDialog(int index){boolean edit=index>=0;Template old=edit?templates.get(index):new Template("","");LinearLayout box=new LinearLayout(this);box.setPadding(dp(16),dp(8),dp(16),0);box.setOrientation(LinearLayout.VERTICAL);EditText n=field("Nom du modèle",old.name),b=field("Texte du SMS",old.body);b.setMinLines(5);b.setGravity(Gravity.TOP);box.addView(n);box.addView(b);new AlertDialog.Builder(this).setTitle(edit?"Modifier le modèle":"Nouveau modèle").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{Template t=new Template(n.getText().toString().trim(),b.getText().toString());if(t.name.isEmpty()||t.body.trim().isEmpty()){toast("Renseigne le nom et le texte.");return;}if(edit)templates.set(index,t);else templates.add(t);saveData();render();}).show();}

    private void pickCsv(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_CSV);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;Uri uri=data.getData();if(request==REQ_LOGO){logoUri=uri;try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}saveData();render();toast("Logo personnalisé enregistré.");return;}if(request==REQ_CSV){importCsv(uri);}}
    private void importCsv(Uri uri){
        int added=0;
        try(InputStream in=getContentResolver().openInputStream(uri);
            BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            String line; boolean firstLine=true; int firstCol=0,lastCol=1,phoneCol=2;
            while((line=r.readLine())!=null){
                if(line.trim().isEmpty())continue;
                List<String> cols=parseCsv(line);
                if(firstLine){
                    firstLine=false;
                    if(!cols.isEmpty())cols.set(0,cols.get(0).replace("\uFEFF",""));
                    int f=-1,l=-1,p=-1;
                    for(int i=0;i<cols.size();i++){
                        String h=normalizeHeader(cols.get(i));
                        if(h.equals("prenom")||h.equals("first")||h.equals("firstname")||h.equals("givenname"))f=i;
                        if(h.equals("nom")||h.equals("name")||h.equals("lastname")||h.equals("surname"))l=i;
                        if(h.equals("telephone")||h.equals("portable")||h.equals("mobile")||h.equals("phone")||h.equals("numero")||h.equals("tel")||h.equals("numerotelephone")||h.equals("numeroportable")||h.equals("numerodetelephone"))p=i;
                    }
                    if(f>=0||l>=0||p>=0){firstCol=f>=0?f:0;lastCol=l>=0?l:1;phoneCol=p>=0?p:2;continue;}
                }
                int max=Math.max(firstCol,Math.max(lastCol,phoneCol));
                if(cols.size()<=max)continue;
                String first=cols.get(firstCol).trim(), last=cols.get(lastCol).trim(), phone=cols.get(phoneCol).trim();
                if(!phone.isEmpty() && !(first.isEmpty()&&last.isEmpty())){contacts.add(new Contact(first,last,phone));added++;}
            }
            saveData();render();
            toast(added+" contact(s) importé(s). Colonnes reconnues : prénom, nom, téléphone.");
        }catch(Exception e){toast("Impossible de lire le CSV. Vérifie le format du fichier.");}
    }
    private String normalizeHeader(String value){
        String n=Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return n.replaceAll("[^a-z0-9]","");
    }
    private List<String> parseCsv(String line){ArrayList<String> out=new ArrayList<>();StringBuilder s=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){s.append('"');i++;}else q=!q;}else if((c==','||c==';'||c=='\t')&&!q){out.add(s.toString());s.setLength(0);}else s.append(c);}out.add(s.toString());return out;}

    private void section(String s){TextView t=label(s,18,Color.rgb(31,45,65));t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(12),0,dp(8));content.addView(t);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=row();l.setPadding(dp(8),dp(6),dp(6),dp(6));l.setBackground(roundBg(Color.WHITE,Color.rgb(232,236,243)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);content.addView(l,p);return l;}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setSingleLine(false);e.setText(value);e.setHint(hint);e.setTextSize(15);e.setPadding(dp(12),dp(8),dp(12),dp(8));e.setBackground(roundBg(Color.WHITE,Color.rgb(220,226,235)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);e.setLayoutParams(p);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(accent);b.setBackground(rippleBg(Color.WHITE,Color.rgb(225,231,240)));return b;}
    private android.graphics.drawable.Drawable rippleBg(int color,int stroke){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(65,0,0,0)),roundBg(color,stroke),null);}
    private Button smallButton(String s){Button b=button(s);b.setMinWidth(dp(42));b.setTextSize(17);return b;}
    private android.graphics.drawable.GradientDrawable roundBg(int color,int stroke){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));d.setStroke(dp(1),stroke);return d;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
