package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.*
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_ListMember
import jp.juggler.subwaytooter.action.makeAccountListNonPseudo
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootList
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.showToast
import jp.juggler.subwaytooter.view.MyListView
import jp.juggler.subwaytooter.view.MyNetworkImageView
import java.util.*

@SuppressLint("InflateParams")
class DlgListMember(
	private val activity : ActMain,
	who : TootAccount,
	_list_owner : SavedAccount
) : View.OnClickListener {
	
	private val dialog : Dialog
	
	private val btnListOwner : Button
	private val btnCreateList : Button
	
	private val account_list : ArrayList<SavedAccount>
	private val target_user_full_acct : String
	
	private var list_owner : SavedAccount? = null
	private var local_who : TootAccount? = null
	
	private val adapter : MyListAdapter
	
	init {
		this.account_list = makeAccountListNonPseudo(activity, null)
		this.target_user_full_acct = _list_owner.getFullAcct(who)
		
		if(_list_owner.isPseudo) {
			this.list_owner = null
		} else {
			this.list_owner = _list_owner
		}
		
		val view = activity.layoutInflater.inflate(R.layout.dlg_list_member, null, false)
		
		val ivUser = view.findViewById<MyNetworkImageView>(R.id.ivUser)
		val tvUserName = view.findViewById<TextView>(R.id.tvUserName)
		val tvUserAcct = view.findViewById<TextView>(R.id.tvUserAcct)
		btnListOwner = view.findViewById(R.id.btnListOwner)
		btnCreateList = view.findViewById(R.id.btnCreateList)
		val listView = view.findViewById<MyListView>(R.id.listView)
		
		this.adapter = MyListAdapter()
		listView.adapter = adapter
		
		btnCreateList.setOnClickListener(this)
		btnListOwner.setOnClickListener(this)
		view.findViewById<View>(R.id.btnClose).setOnClickListener(this)
		
		ivUser.setImageUrl(App1.pref, 	Styler.calcIconRound(ivUser.layoutParams), who.avatar_static, who.avatar)
		val user_name_invalidator = NetworkEmojiInvalidator(activity.handler, tvUserName)
		val name = who.decodeDisplayName(activity)
		tvUserName.text = name
		user_name_invalidator.register(name)
		tvUserAcct.text = target_user_full_acct
		
		setListOwner(list_owner)
		
		this.dialog = Dialog(activity)
		
		val w = dialog.window
		if(w != null) {
			w.setFlags(0, Window.FEATURE_NO_TITLE)
			w.setLayout(
				WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
			)
		}
		
		dialog.setTitle(R.string.your_lists)
		dialog.setContentView(view)
	}
	
	fun show() {
		val w = dialog.window
		w?.setLayout(
			WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
		)
		dialog.show()
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnClose -> try {
				dialog.dismiss()
			} catch(ignored : Throwable) {
			}
			
			R.id.btnListOwner -> {
				AccountPicker.pick(
					activity,
					bAllowPseudo = false,
					bAuto = false,
					accountListArg = account_list
				) { ai ->
					setListOwner(ai)
				}
				
			}
			
			R.id.btnCreateList -> openListCreator()
		}
	}
	
	// リストオーナボタンの文字列を更新する
	// リスト一覧を取得する
	private fun setListOwner(a : SavedAccount?) {
		this.list_owner = a
		if(a == null) {
			btnListOwner.setText(R.string.not_selected)
			btnListOwner.setTextColor(Styler.getAttributeColor(activity, android.R.attr.textColorPrimary))
			btnListOwner.setBackgroundResource(R.drawable.btn_bg_transparent)
			//
			
		} else {
			val acct = a.acct
			val ac = AcctColor.load(acct)
			val nickname = if(AcctColor.hasNickname(ac)) ac.nickname else acct
			btnListOwner.text = nickname
			
			if(AcctColor.hasColorBackground(ac)) {
				btnListOwner.setBackgroundColor(ac.color_bg)
			} else {
				btnListOwner.setBackgroundResource(R.drawable.btn_bg_transparent)
			}
			if(AcctColor.hasColorForeground(ac)) {
				btnListOwner.setTextColor(ac.color_fg)
			} else {
				btnListOwner.setTextColor(Styler.getAttributeColor(activity, android.R.attr.textColorPrimary))
			}
		}
		
		loadLists()
	}
	
	// リストの一覧とターゲットユーザの登録状況を取得する
	private fun loadLists() {
		val list_owner = this.list_owner
		
		if(list_owner == null) {
			showList(null)
			return
		}
		
		TootTaskRunner(activity).run(list_owner , object : TootTask {
			
			var new_list : ArrayList<TootList>? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				// リストに追加したいアカウントの自タンスでのアカウントIDを取得する
				var result = client.syncAccountByAcct(list_owner,target_user_full_acct)
				local_who = result?.data as? TootAccount
				
				
				val local_who = this@DlgListMember.local_who
					?: return TootApiResult(activity.getString(R.string.account_sync_failed))
				
				// リスト登録状況を取得
				result = client.request("/api/v1/accounts/" + local_who .id + "/lists")
				var jsonArray = result?.jsonArray ?:return result
				
				// 結果を解釈する
				val set_registered = HashSet<EntityId>()
				for(a in parseList(::TootList,jsonArray)) {
					set_registered.add(a.id)
				}
				
				// リスト一覧を取得
				result = client.request("/api/v1/lists")
				jsonArray = result?.jsonArray ?: return result

				val new_list = parseList(::TootList,jsonArray)
				new_list.sort()
				
				this.new_list = new_list
				
				// isRegistered を設定する
				for(a in new_list) {
					if(set_registered.contains(a.id)) a.isRegistered = true
				}
				
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				showList(new_list)
				
				result ?: return // cancelled.
				
				val error = result.error
				if( error?.isNotEmpty() == true && result.response?.code() == 404 ) {
					showToast(activity, true, result.error)
				}
				
			}
		})
		
	}
	
	private fun showList(_list : ArrayList<TootList>?) {
		btnCreateList.isEnabled = _list != null
		adapter.item_list.clear()
		when {
			_list == null -> adapter.item_list.add(ErrorItem(activity.getString(R.string.cant_access_list)))
			_list.isEmpty() -> adapter.item_list.add(ErrorItem(activity.getString(R.string.list_not_created)))
			else -> adapter.item_list.addAll(_list)
		}
		adapter.notifyDataSetChanged()
	}
	
	private fun openListCreator() {
		DlgTextInput.show(activity, activity.getString(R.string.list_create), null, object : DlgTextInput.Callback {
			
			override fun onEmptyError() {
				showToast(activity, false, R.string.list_name_empty)
			}
			
			override fun onOK(dialog : Dialog, text : String) {
				val list_owner = this@DlgListMember.list_owner

				if(list_owner == null) {
					showToast(activity, false, "list owner is not selected.")
					return
				}
				
				Action_List.create(activity, list_owner , text, object : Action_List.CreateCallback {
					override fun onCreated(list : TootList) {
						try {
							dialog.dismiss()
						} catch(ignored : Throwable) {
						}
						
						loadLists()
						
					}
				})
			}
			
		})
	}
	
	internal class ErrorItem(val message : String)
	
	private inner class MyListAdapter : BaseAdapter() {
		internal val item_list = ArrayList<Any>()
		
		override fun getCount() : Int {
			return item_list.size
		}
		
		override fun getItem(position : Int) : Any? {
			return if(position >= 0 && position < item_list.size) item_list[position] else null
		}
		
		override fun getItemId(position : Int) : Long {
			return 0
		}
		
		override fun getViewTypeCount() : Int {
			return 2
		}
		
		override fun getItemViewType(position : Int) : Int {
			val o = getItem(position)
			return if(o is TootList) 0 else 1
		}
		
		override fun getView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view:View
			val o = getItem(position)
			when(o) {
				is TootList -> {
					val holder : VH_List
					if(viewOld != null) {
						view = viewOld
						holder = view.tag as VH_List
					} else {
						view = activity.layoutInflater.inflate(R.layout.lv_list_member_list, parent, false)
						holder = VH_List(view)
						view .tag = holder
					}
					holder.bind(o)
				}
				is ErrorItem -> {
					val holder : VH_Error
					if(viewOld != null) {
						view = viewOld
						holder = view.tag as VH_Error
					} else {
						view = activity.layoutInflater.inflate(R.layout.lv_list_member_error, parent, false)
						holder = VH_Error(view)
						view .tag = holder
					}
					holder.bind(o)
				}
				else -> view = activity.layoutInflater.inflate(R.layout.lv_list_member_error, parent, false)
			}
			return view
		}
	}
	
	internal inner class VH_List(view : View) : CompoundButton.OnCheckedChangeListener, Action_ListMember.Callback {
		private val cbItem : CheckBox
		private var bBusy : Boolean = false
		var item : TootList? = null
		
		init {
			this.cbItem = view.findViewById(R.id.cbItem)
			cbItem.setOnCheckedChangeListener(this)
		}
		
		fun bind(item : TootList) {
			bBusy = true

			this.item = item
			cbItem.text = item.title
			cbItem.isChecked = item.isRegistered

			bBusy = false
		}
		
		override fun onCheckedChanged(view : CompoundButton, isChecked : Boolean) {
			if(bBusy) {
				// ユーザ操作以外で変更されたなら何もしない
				return
			}

			val list_owner = this@DlgListMember.list_owner
			if(list_owner == null) {
				showToast(activity, false, "list owner is not selected")
				return
			}
			
			val local_who = this@DlgListMember.local_who
			if(local_who == null) {
				showToast(activity, false, "target user is not synchronized")
				return
			}

			val item = this.item ?:return
			
			// 状態をサーバに伝える
			if(isChecked) {
				Action_ListMember.add(activity, list_owner , item.id, local_who , false, this)
			} else {
				Action_ListMember.delete(activity, list_owner , item.id, local_who , this)
			}
		}
		
		override fun onListMemberUpdated(willRegistered : Boolean, bSuccess : Boolean) {
			if(! bSuccess) {
				item?.isRegistered = ! willRegistered
				adapter.notifyDataSetChanged()
			}
		}
	}
	
	internal inner class VH_Error(view : View) {
		private val tvError : TextView
		
		init {
			this.tvError = view.findViewById(R.id.tvError)
		}
		
		fun bind(o : ErrorItem) {
			this.tvError.text = o.message
		}
	}
}
