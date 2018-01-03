package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.api.TootParser;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootResults {
	
	private static final LogCategory log = new LogCategory( "TootResults" );
	
	//	An array of matched Accounts
	public TootAccount.List accounts;
	
	//	An array of matched Statuses
	public TootStatus.List statuses;
	
	//	An array of matched hashtags, as strings
	public ArrayList< String > hashtags;
	
	@Nullable
	public static TootResults parse( @NonNull TootParser parser, JSONObject src ){
		try{
			if( src == null ) return null;
			TootResults dst = new TootResults();
			dst.accounts = TootAccount.parseList( parser.context, parser.access_info, src.optJSONArray( "accounts" ) );
			dst.statuses = TootStatus.parseList( parser, src.optJSONArray( "statuses" ) );
			dst.hashtags = Utils.parseStringArray( src.optJSONArray( "hashtags" ) );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootResults.parse failed." );
			return null;
		}
	}
}
