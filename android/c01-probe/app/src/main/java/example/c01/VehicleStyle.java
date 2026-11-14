package example.c01;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
/** Values traced in research/pleos-design-spec/applied-tokens.json. */
final class VehicleStyle {
 static int BG,PANEL,TEXT,MUTED,ACCENT,PRESSED,SURFACE;
 static boolean night;
 static Typeface font;
 static void configure(Context c){
  night=(c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
  BG=Color.parseColor(night?"#313236":"#FFFFFF");PANEL=Color.parseColor(night?"#1D1E21":"#F7F8FA");SURFACE=Color.parseColor(night?"#131417":"#FFFFFF");
  TEXT=Color.parseColor(night?"#FFFFFF":"#131417");MUTED=Color.parseColor(night?"#FFFFFF":"#44464E");ACCENT=Color.parseColor("#44464E");PRESSED=Color.parseColor(night?"#686A72":"#313236");
  try{font=new Typeface.Builder(c.getAssets(),"AstaSans.ttf").setFontVariationSettings("'wght' 400").setWeight(400).build();}catch(RuntimeException e){font=Typeface.create("sans-serif",Typeface.NORMAL);}
 }
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static TextView text(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);
  int token=size>=30?40:size>=24?32:size>=20?28:24;
  t.setTextSize(token);t.setTypeface(size>=24?Typeface.create(font,700,false):font);t.setTextColor(color);t.setPadding(0,dp(c,8),0,dp(c,8));return t;}
 static void button(Button b,boolean primary){Context c=b.getContext();b.setTextSize(30);b.setAllCaps(false);b.setGravity(17);b.setMinHeight(dp(c,100));b.setPadding(dp(c,24),dp(c,16),dp(c,24),dp(c,16));b.setTypeface(font,Typeface.NORMAL);
 int[][] states={new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},new int[]{}};
 b.setTextColor(new ColorStateList(states,new int[]{MUTED,primary?Color.WHITE:TEXT,primary?Color.WHITE:TEXT}));
 GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(c,32));
 int basic=Color.argb(13,19,20,23);int basicPressed=Color.argb(26,19,20,23);
 shape.setColor(new ColorStateList(states,new int[]{PANEL,primary?PRESSED:basicPressed,primary?ACCENT:basic}));b.setBackground(shape);
 LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(c,8),0,dp(c,8));b.setLayoutParams(lp);}
}
