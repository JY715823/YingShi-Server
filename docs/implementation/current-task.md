
---

# 5. Server `current-task.md`

```md
# Current Task: Stage 12.7 - 鍏ㄥ眬娴佺晠搴﹁仈璋冩敮鎸?

## 鑳屾櫙

Android 杩涘叆 Stage 12.7锛岄噸鐐规槸鍏ㄥ眬娴佺晠搴﹀拰鍑忓皯閲嶅璇锋眰銆係erver 榛樿涓嶆敼涓氬姟閫昏緫锛屽彧鍦ㄦ帴鍙ｅ垎椤点€佹煡璇㈠瓧娈垫垨濂戠害璇存槑涓嶈冻瀵艰嚧 Android 杩囧害璇锋眰鏃跺仛鏈€灏忎慨姝ｆ垨鏂囨。璇存槑銆?

## 鐩爣

1. 妫€鏌ュ垪琛ㄦ煡璇㈡槸鍚︽敮鎸?Android 褰撳墠鍒嗛〉 / 鍒嗘壒鍔犺浇闇€姹傘€?
2. 妫€鏌ュ獟浣撶缉鐣ュ浘瀛楁鏄惁绋冲畾杩斿洖銆?
3. 妫€鏌ュ笘瀛愯鎯呮槸鍚﹂伩鍏?Android 蹇呴』棰濆璇锋眰杩囧鏁版嵁銆?
4. 妫€鏌ョ郴缁熷獟浣撶浉鍏冲绾﹁鏄庢槸鍚︽竻妤氥€?
5. 濡傛棤蹇呰锛屼笉淇敼 Server 浠ｇ爜銆?

## 涓嶅仛鍐呭

- 涓嶅仛 OSS
- 涓嶅仛杞爜
- 涓嶅仛鏂板ぇ鎺ュ彛
- 涓嶆敼鏉冮檺浣撶郴
- 涓嶆敼涓氬姟瑙勫垯
- 涓嶅仛 WebSocket / 鎺ㄩ€?

## 楠屾敹

1. 濂戠害鏂囨。鏀寔 Android 娴佺晠搴︿紭鍖栥€?
2. 濡備慨鏀?Server锛宮vnw test 閫氳繃銆?
3. 濡傛湭淇敼 Server锛屾渶缁堣鏄?Server 鏈慨鏀广€?

## Stage 12.7 Server Sync Note

- No server business logic change is required in this pass.
- Android now depends even more on stable preview-sized URLs for list surfaces, and comment mutations are expected to stay lightweight without implying a required full media-feed reload.

## Post-12.7 Media Ownership Sync

- Server business logic was minimally adjusted because app media can now be imported without belonging to a post.
- `/api/media/feed` returns all active media in the current space. `postIds` may be empty and still represents a valid photo-feed item.
- Viewer “所属帖子” should only appear when `postIds` is non-empty; import-only media remains visible in the app photo feed.

## Post-12.7 Targeted Fix Follow-up

- `/api/media/files/{mediaId}` may serve a deleted media file when the media id belongs to the current space. Android uses this to render read-only trash detail previews for `mediaRemoved`, `mediaSystemDeleted`, and `postDeleted` entries.
- Normal feed and post APIs still filter deleted media through their existing repository queries; the file endpoint relaxation is only a binary-resource lookup and does not make deleted media reappear in active lists.
- Local seed image preparation now reuses an existing readable seed file instead of always overwriting it. This avoids Windows file-lock failures when tests run while a dev server or image reader still has a seed file open.


