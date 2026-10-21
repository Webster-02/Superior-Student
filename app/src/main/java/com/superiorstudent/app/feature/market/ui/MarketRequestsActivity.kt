package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.ui.AppThemePreferences
import kotlin.math.min

/**
 * First native implementation of the supplied Requests mockup.
 * The three cards below are reference fixtures; live Firestore reads/cache are a separate step.
 */
class MarketRequestsActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var searchRow: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var list: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var categoryChips: Map<String, TextView>
    private var selectedCategory = "All"
    private var selectedSort = MarketRequestSort.REFERENCE_ORDER
    private var searchVisible = false

    private val referenceRequests = listOf(
        MarketRequestPreview("reference-design","Design","BSOPM",
            "Poster for Optometry Society health camp",
            "Need an A3 poster, 2 colour options, text will be provided. Canva or Photoshop is fine.",
            "Rs. 1,500",1500,"Due in 3 days",3,4,"Hamza Ali","HA","· 2h ago",true,R.color.market_sky,0),
        MarketRequestPreview("reference-notes","Notes","Anatomy",
            "Handwritten notes for Ocular Anatomy (Ch 1-5)",
            "Looking for clear, complete handwritten notes. Scan or photos are fine, can meet on campus.",
            "Negotiable",0,"Due in 5 days",5,2,"Sara Ahmed","SA","· 5h ago",false,R.color.market_mint,1),
        MarketRequestPreview("reference-coding","Coding",null,
            "Help fixing a Python lab program",
            "Loop and file handling error. Need someone to explain what is wrong, not do the whole lab.",
            "Free help",0,"Due tomorrow",1,7,"Bilal Raza","BR","· 1d ago",false,R.color.market_peach,2)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(8); marginStart = dp(16); marginEnd = dp(16)
        })
        searchRow = buildSearchRow().apply { visibility = View.GONE }
        content.addView(searchRow, LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(6); marginStart = dp(16); marginEnd = dp(16)
        })
        content.addView(buildTabs(), LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(6); marginStart = dp(16); marginEnd = dp(16)
        })
        content.addView(buildCategoryRow(), LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(12)
        })

        val panel = FrameLayout(this)
        content.addView(panel, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        val scroll = ScrollView(this).apply {
            clipToPadding = false; isFillViewport = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        panel.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(96))
        }
        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))
        emptyState = makeText("No requests found\nTry another category or search term.",14f,false,color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(24),dp(24),dp(24),dp(24))
        }
        panel.addView(emptyState, FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))

        val fab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(20),0,dp(20),0)
            background = rounded(color(R.color.market_match),99f)
            elevation = dp(8).toFloat()
            isClickable = true; isFocusable = true
            addView(MarketIconView(this@MarketRequestsActivity,MarketIcon.PLUS,color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20),dp(20)).apply { marginEnd=dp(8) })
            addView(makeText("Post a task",14f,true,color(R.color.market_match_ink)))
        }
        fab.setOnClickListener { toast("Post a task is a separate reference-screen step.") }
        root.addView(fab,FrameLayout.LayoutParams(-2,dp(52),Gravity.BOTTOM or Gravity.END).apply {
            marginEnd=dp(16); bottomMargin=dp(22)
        })

        applyWindowInsets()
        wireActions()
        updateCategoryChips()
        renderRequests()
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(searchVisible) closeSearch() else finish() }
        })
    }

    private fun buildHeader(): View {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        row.addView(iconButton(MarketIcon.BACK,"Back",40) { finish() })
        val titleColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL }
        titleColumn.addView(makeText("Market",20f,true,color(R.color.market_ink)).apply { letterSpacing=-0.02f })
        titleColumn.addView(makeText("Help each other on campus",12f,false,color(R.color.market_ink2)))
        row.addView(titleColumn,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12); marginEnd=dp(8) })
        row.addView(iconButton(MarketIcon.SEARCH,"Search requests",40) {
            if(searchVisible) closeSearch() else openSearch()
        },LinearLayout.LayoutParams(dp(40),dp(40)).apply { marginEnd=dp(10) })
        val menu=FrameLayout(this).apply {
            background=rounded(color(R.color.market_card),14f,color(R.color.market_line))
            isClickable=true; isFocusable=true; contentDescription="Market menu"
        }
        menu.addView(MarketIconView(this,MarketIcon.MORE,color(R.color.market_ink)),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
        menu.addView(View(this).apply { background=rounded(color(R.color.market_menu_dot),99f) },
            FrameLayout.LayoutParams(dp(8),dp(8),Gravity.TOP or Gravity.END).apply { topMargin=dp(7); marginEnd=dp(7) })
        menu.setOnClickListener { showMenu(menu) }
        row.addView(menu,LinearLayout.LayoutParams(dp(40),dp(40)))
        return row
    }

    private fun buildSearchRow(): LinearLayout {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            background=rounded(color(R.color.market_card),16f,color(R.color.market_line))
            setPadding(dp(10),0,dp(8),0)
        }
        row.addView(MarketIconView(this,MarketIcon.SEARCH,color(R.color.market_ink2)),
            LinearLayout.LayoutParams(dp(18),dp(18)).apply { marginEnd=dp(8) })
        searchInput=EditText(this).apply {
            setSingleLine(true); textSize=12f; typeface=font(false)
            setTextColor(color(R.color.market_ink)); setHintTextColor(color(R.color.market_ink2))
            hint="Search tasks"; background=null
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            inputType=android.text.InputType.TYPE_CLASS_TEXT; setPadding(0,0,0,0)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { renderRequests() }
                override fun afterTextChanged(s:Editable?)=Unit
            })
        }
        row.addView(searchInput,LinearLayout.LayoutParams(0,-1,1f))
        row.addView(makeText("×",22f,false,color(R.color.market_ink2)).apply {
            gravity=Gravity.CENTER; setOnClickListener { closeSearch() }
        },LinearLayout.LayoutParams(dp(28),-1))
        return row
    }

    private fun buildTabs(): View {
        val outer=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; setPadding(dp(5),dp(5),dp(5),dp(5))
            background=rounded(color(R.color.market_card),20f,color(R.color.market_line))
        }
        outer.addView(makeText("Requests",13f,true,color(R.color.market_segment_ink)).apply {
            gravity=Gravity.CENTER; background=rounded(color(R.color.market_segment),15f)
        },LinearLayout.LayoutParams(0,-1,1f))
        outer.addView(makeText("Offers",13f,true,color(R.color.market_ink2)).apply {
            gravity=Gravity.CENTER; setOnClickListener { startActivity(Intent(this@MarketRequestsActivity, MarketOffersActivity::class.java)) }
        },LinearLayout.LayoutParams(0,-1,1f))
        return outer
    }

    private fun buildCategoryRow(): View {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(16),0,dp(16),0)
        }
        val scroll=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false; overScrollMode=View.OVER_SCROLL_NEVER; clipToPadding=false
        }
        val chips=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val names=listOf("All","Design","Notes","Coding","Tutoring")
        val chipMap=linkedMapOf<String,TextView>()
        names.forEachIndexed { index,name ->
            val chip=makeText(name,12f,true,color(R.color.market_ink)).apply {
                gravity=Gravity.CENTER; setPadding(dp(14),0,dp(14),0)
                background=rounded(color(R.color.market_card),99f,color(R.color.market_line))
                setOnClickListener { selectedCategory=name; updateCategoryChips(); renderRequests() }
            }
            if(index>0) chips.addView(chip,LinearLayout.LayoutParams(-2,dp(38)).apply { marginStart=dp(8) })
            else chips.addView(chip,LinearLayout.LayoutParams(-2,dp(38)))
            chipMap[name]=chip
        }
        categoryChips=chipMap
        scroll.addView(chips,FrameLayout.LayoutParams(-2,-1))
        row.addView(scroll,LinearLayout.LayoutParams(0,-1,1f))
        row.addView(iconButton(MarketIcon.FILTER,"Sort requests",38) { showSortDialog() },
            LinearLayout.LayoutParams(dp(38),dp(38)).apply { marginStart=dp(8) })
        return row
    }

    private fun updateCategoryChips() {
        if(!::categoryChips.isInitialized) return
        categoryChips.forEach { (name,chip) ->
            val selected=name==selectedCategory
            chip.background=rounded(color(if(selected) R.color.market_segment else R.color.market_card),99f,
                if(selected) null else color(R.color.market_line))
            chip.setTextColor(color(if(selected) R.color.market_segment_ink else R.color.market_ink))
        }
    }

    private fun iconButton(icon:MarketIcon,label:String,size:Int,action:()->Unit):View =
        FrameLayout(this).apply {
            background=rounded(color(R.color.market_card),14f,color(R.color.market_line))
            isClickable=true; isFocusable=true; contentDescription=label
            val iconSize=if(icon==MarketIcon.FILTER) 18 else 20
            addView(MarketIconView(this@MarketRequestsActivity,icon,color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(iconSize),dp(iconSize),Gravity.CENTER))
            setOnClickListener { action() }
        }.also { it.layoutParams=ViewGroup.LayoutParams(dp(size),dp(size)) }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowCompat.getInsetsController(window,root).apply {
            isAppearanceLightStatusBars=!AppThemePreferences.isNight(this@MarketRequestsActivity)
            isAppearanceLightNavigationBars=!AppThemePreferences.isNight(this@MarketRequestsActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun wireActions() {
        searchInput.setOnEditorActionListener { _,actionId,_ ->
            if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken,0)
                true
            } else false
        }
    }

    private fun openSearch() {
        searchVisible=true; searchRow.visibility=View.VISIBLE; searchInput.requestFocus()
        searchInput.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(searchInput,InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeSearch() {
        searchVisible=false; searchInput.setText(""); searchRow.visibility=View.GONE
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken,0)
        renderRequests()
    }

    private fun showMenu(anchor:View) {
        PopupMenu(this,anchor).apply {
            listOf("My tasks","My offers","Notices","Help and safety","Market settings").forEach { menu.add(it) }
            setOnMenuItemClickListener { item -> toast("${item.title} is coming in its own reference-screen step."); true }
            show()
        }
    }

    private fun showSortDialog() {
        val choices=arrayOf("Reference order","Newest first","Deadline soonest","Budget highest")
        AlertDialog.Builder(this).setTitle("Sort requests")
            .setSingleChoiceItems(choices,selectedSort.ordinal) { dialog,which ->
                selectedSort=MarketRequestSort.values()[which]; renderRequests(); dialog.dismiss()
            }.setNegativeButton("Cancel",null).show()
    }

    private fun renderRequests() {
        if(!::list.isInitialized) return
        list.removeAllViews()
        val shown=MarketRequestsUiPolicy.filterAndSort(referenceRequests,selectedCategory,searchInput.text?.toString().orEmpty(),selectedSort)
        shown.forEach { item ->
            list.addView(buildRequestCard(item),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
        emptyState.visibility=if(shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildRequestCard(item:MarketRequestPreview):View {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14))
            background=rounded(color(R.color.market_card),22f,color(R.color.market_line))
            isClickable=true; isFocusable=true
        }
        val tags=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        tags.addView(makeText(item.category,11f,true,color(R.color.market_ink)).apply {
            setPadding(dp(10),dp(3),dp(10),dp(3)); background=rounded(color(categoryColor(item.category)),99f)
        })
        item.courseTag?.let { course ->
            tags.addView(makeText(course,11f,true,color(R.color.market_ink)).apply {
                setPadding(dp(10),dp(3),dp(10),dp(3)); background=rounded(color(R.color.market_neutral_chip),99f)
            },LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(6) })
        }
        tags.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        if(item.matchesUser) {
            val match=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER
                setPadding(dp(8),dp(5),dp(8),dp(5)); background=rounded(color(R.color.market_match),99f)
                addView(MarketIconView(this@MarketRequestsActivity,MarketIcon.SPARKLE,color(R.color.market_match_ink)),
                    LinearLayout.LayoutParams(dp(12),dp(12)))
                addView(makeText("Matches you",11f,true,color(R.color.market_match_ink)),
                    LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(4) })
            }
            tags.addView(match,LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(8) })
        }
        card.addView(tags)
        card.addView(makeText(item.title,15f,true,color(R.color.market_ink)).apply {
            maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; letterSpacing=-0.01f
        },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        card.addView(makeText(item.description,12f,false,color(R.color.market_ink2)).apply {
            maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
        },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(3) })

        val meta=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        meta.addView(makeText(item.budgetLabel,14f,true,color(R.color.market_ink)))
        val due=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            addView(MarketIconView(this@MarketRequestsActivity,MarketIcon.CLOCK,color(R.color.market_ink2)),
                LinearLayout.LayoutParams(dp(13),dp(13)).apply { marginEnd=dp(3) })
            addView(makeText(item.dueLabel,12f,false,color(R.color.market_ink2)))
        }
        meta.addView(due,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(8) })
        meta.addView(makeText("${item.proposalCount} proposals",12f,false,color(R.color.market_ink2)))
        card.addView(meta,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        card.addView(View(this).apply { setBackgroundColor(color(R.color.market_line)) },
            LinearLayout.LayoutParams(-1,dp(1)).apply { topMargin=dp(10) })

        val author=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        author.addView(makeText(item.initials,8f,true,color(R.color.market_ink)).apply {
            gravity=Gravity.CENTER; background=oval(color(item.avatarColor))
        },LinearLayout.LayoutParams(dp(24),dp(24)))
        author.addView(makeText(item.posterName,12f,false,color(R.color.market_ink)),
            LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(8) })
        author.addView(makeText(item.ageLabel,12f,false,color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(6) })
        card.addView(author,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        card.setOnClickListener { toast("Task detail is a separate reference-screen step.") }
        return card
    }

    private fun makeText(value:String,size:Float,medium:Boolean,textColor:Int):TextView=TextView(this).apply {
        text=value; textSize=size; typeface=font(medium); setTextColor(textColor); includeFontPadding=false
        gravity=Gravity.CENTER_VERTICAL
        if(size<=12f) setLineSpacing(dp(2).toFloat(),1f)
    }

    private fun font(medium:Boolean):Typeface=
        ResourcesCompat.getFont(this,if(medium) R.font.poppins_medium else R.font.poppins)
            ?: Typeface.create(if(medium) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)

    private fun rounded(fill:Int,radius:Float,stroke:Int?=null)=GradientDrawable().apply {
        shape=GradientDrawable.RECTANGLE; cornerRadius=dp(radius).toFloat(); setColor(fill)
        stroke?.let { setStroke(dp(1),it) }
    }
    private fun oval(fill:Int)=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(fill) }
    private fun categoryColor(category:String)=when(category) {
        "Design"->R.color.market_peach; "Notes"->R.color.market_lav; "Coding"->R.color.market_mint
        else->R.color.market_sky
    }
    private fun color(id:Int)=getColor(id)
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun dp(value:Float)=(value*resources.displayMetrics.density).toInt()
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
}

internal data class MarketRequestPreview(
    val id:String,val category:String,val courseTag:String?,val title:String,val description:String,
    val budgetLabel:String,val budgetValue:Int,val dueLabel:String,val dueDays:Int,val proposalCount:Int,
    val posterName:String,val initials:String,val ageLabel:String,val matchesUser:Boolean,
    val avatarColor:Int,val recencyOrder:Int
)
internal enum class MarketRequestSort { REFERENCE_ORDER, NEWEST, DEADLINE, BUDGET }
internal object MarketRequestsUiPolicy {
    fun filterAndSort(items:List<MarketRequestPreview>,category:String,query:String,sort:MarketRequestSort):List<MarketRequestPreview> {
        val q=query.trim()
        val filtered=items.filter { item ->
            (category=="All"||item.category.equals(category,true)) && (q.isBlank()||
                item.title.contains(q,true)||item.category.contains(q,true)||item.courseTag.orEmpty().contains(q,true))
        }
        return when(sort) {
            MarketRequestSort.REFERENCE_ORDER,MarketRequestSort.NEWEST->filtered.sortedBy { it.recencyOrder }
            MarketRequestSort.DEADLINE->filtered.sortedBy { it.dueDays }
            MarketRequestSort.BUDGET->filtered.sortedByDescending { it.budgetValue }
        }
    }
}

private enum class MarketIcon { BACK,SEARCH,MORE,FILTER,CLOCK,PLUS,SPARKLE }
private class MarketIconView(context:Context,private val icon:MarketIcon,private val tint:Int):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=tint; style=Paint.Style.STROKE; strokeWidth=1.8f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val unit=min(width,height)/24f
        if(unit<=0f)return
        val saved=canvas.save()
        canvas.translate((width-24f*unit)/2f,(height-24f*unit)/2f); canvas.scale(unit,unit)
        val path=Path()
        when(icon) {
            MarketIcon.BACK->{path.moveTo(19f,12f);path.lineTo(5f,12f);path.moveTo(5f,12f);path.lineTo(11f,6f);path.moveTo(5f,12f);path.lineTo(11f,18f);canvas.drawPath(path,paint)}
            MarketIcon.SEARCH->{canvas.drawCircle(10f,10f,7f,paint);path.moveTo(15f,15f);path.lineTo(21f,21f);canvas.drawPath(path,paint)}
            MarketIcon.MORE->{paint.style=Paint.Style.FILL;canvas.drawCircle(5f,12f,1.45f,paint);canvas.drawCircle(12f,12f,1.45f,paint);canvas.drawCircle(19f,12f,1.45f,paint);paint.style=Paint.Style.STROKE}
            MarketIcon.FILTER->{path.moveTo(4f,6f);path.lineTo(20f,6f);path.moveTo(7f,12f);path.lineTo(17f,12f);path.moveTo(10f,18f);path.lineTo(14f,18f);canvas.drawPath(path,paint)}
            MarketIcon.CLOCK->{canvas.drawCircle(12f,12f,9f,paint);path.moveTo(12f,7f);path.lineTo(12f,12f);path.lineTo(15f,15f);canvas.drawPath(path,paint)}
            MarketIcon.PLUS->{path.moveTo(12f,5f);path.lineTo(12f,19f);path.moveTo(5f,12f);path.lineTo(19f,12f);canvas.drawPath(path,paint)}
            MarketIcon.SPARKLE->{path.moveTo(12f,3f);path.lineTo(13.8f,8.2f);path.lineTo(19f,10f);path.lineTo(13.8f,11.8f);path.lineTo(12f,17f);path.lineTo(10.2f,11.8f);path.lineTo(5f,10f);path.lineTo(10.2f,8.2f);path.close();canvas.drawPath(path,paint)}
        }
        canvas.restoreToCount(saved)
    }
}
