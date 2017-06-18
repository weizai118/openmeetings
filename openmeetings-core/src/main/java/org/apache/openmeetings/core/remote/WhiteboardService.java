/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.core.remote;

import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_DEFAULT_LANG_KEY;
import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.collections4.ComparatorUtils;
import org.apache.openmeetings.core.data.whiteboard.WhiteboardCache;
import org.apache.openmeetings.core.data.whiteboard.WhiteboardObjectSyncManager;
import org.apache.openmeetings.core.remote.red5.ScopeApplicationAdapter;
import org.apache.openmeetings.db.dao.basic.ConfigurationDao;
import org.apache.openmeetings.db.dao.label.LabelDao;
import org.apache.openmeetings.db.dao.server.ISessionManager;
import org.apache.openmeetings.db.dao.server.SessiondataDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.dto.room.Cliparts;
import org.apache.openmeetings.db.dto.room.Whiteboard;
import org.apache.openmeetings.db.dto.room.WhiteboardSyncLockObject;
import org.apache.openmeetings.db.dto.room.Whiteboards;
import org.apache.openmeetings.db.entity.room.Client;
import org.apache.openmeetings.db.entity.server.Sessiondata;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.db.util.AuthLevelUtil;
import org.apache.openmeetings.util.OmFileHelper;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author sebastianwagner
 *
 */
public class WhiteboardService implements IPendingServiceCallback {
	private static final Logger log = Red5LoggerFactory.getLogger(WhiteboardService.class, webAppRootKey);
	@Autowired
	private UserDao userDao;
	@Autowired
	private ScopeApplicationAdapter scopeAdapter;
	@Autowired
	private ISessionManager sessionManager;
	@Autowired
	private WhiteboardObjectSyncManager wbListManager;
	@Autowired
	private WhiteboardCache wbCache;
	@Autowired
	private SessiondataDao sessionDao;
	@Autowired
	private ConfigurationDao cfgDao;

	public boolean getNewWhiteboardId(String name) {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			Long whiteBoardId = wbCache.getNewWhiteboardId(roomId, name);
			scopeAdapter.sendMessageAll(Arrays.asList("newWhiteboard", whiteBoardId, name));
		} catch (Exception e) {
			log.error("[getNewWhiteboardId]", e);
			return false;
		}
		return true;
	}

	public boolean deleteWhiteboard(Long whiteBoardId) {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			Whiteboards whiteboards = wbCache.get(roomId);
			Object returnValue = whiteboards.getWhiteboards().remove(whiteBoardId);

			log.debug(" :: whiteBoardId :: " + whiteBoardId);

			wbCache.set(roomId, whiteboards);

			if (returnValue != null) {
				return true;
			}
		} catch (Exception err) {
			log.error("[deleteWhiteboard]", err);
		}
		return false;
	}

	public Map<Long, Whiteboard> getRoomItemsBy() {
		Map<Long, Whiteboard> result = new LinkedHashMap<>();
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			log.debug("getRoomItems: " + roomId);
			Whiteboards whiteboards = wbCache.get(roomId);

			if (whiteboards.getWhiteboards().isEmpty()) {
				Long langId = null;
				{
					Long userId = currentClient.getUserId();
					if (userId != null && userId.longValue() < 0) {
						userId = -userId;
					}
					User u = userDao.get(userId);
					langId = u == null ? cfgDao.getConfValue(CONFIG_DEFAULT_LANG_KEY, Long.class, "1") : u.getLanguageId();
				}
				wbCache.getNewWhiteboardId(roomId, LabelDao.getString("615", langId));
				log.debug("Init New Room List");
				whiteboards = wbCache.get(roomId);
			}
			whiteboards.getWhiteboards().entrySet().stream()
					.sorted(Map.Entry.<Long, Whiteboard>comparingByKey().reversed())
					.forEachOrdered(x -> result.put(x.getKey(), x.getValue()));
		} catch (Exception err) {
			log.error("[getRoomItemsBy]", err);
		}
		return result;
	}

	public boolean rename(Long wbId, String name) {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			Whiteboards whiteboards = wbCache.get(roomId);
			Whiteboard wb = whiteboards.getWhiteboards().get(wbId);
			wb.setName(name);

			log.debug(" :: rename whiteBoard :: id = {}, name = {}", wbId, name);
			scopeAdapter.sendMessageAll(Arrays.asList("renameWhiteboard", wbId, name));
		} catch (Exception err) {
			log.error("[rename]", err);
			return false;
		}
		return true;
	}

	/**
	 * change the draw status of a user, allow disallow him to draw anybody
	 * besides the Moderator to draw on the whiteboard, only a Moderator is
	 * allowed to trigger this function
	 *
	 * @param sid
	 * @param publicSID
	 * @param canDraw
	 * @return null in case of success, false otherwise
	 */
	public boolean setCanDraw(String sid, String publicSID, boolean canDraw) {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);

			Sessiondata sd = sessionDao.check(sid);
			if (AuthLevelUtil.hasUserLevel(userDao.getRights(sd.getUserId()))) {
				if (currentClient.getIsMod()) {
					Client rcl = sessionManager.getClientByPublicSID(publicSID, null);

					if (rcl != null) {
						rcl.setCanDraw(canDraw);
						sessionManager.updateClientByStreamId(rcl.getStreamid(), rcl, false, null);

						Map<Integer, Object> newMessage = new HashMap<>();
						newMessage.put(0, "updateDrawStatus");
						newMessage.put(1, rcl);
						scopeAdapter.sendMessageWithClientWithSyncObject(newMessage, true);
						return true;
					}
				}
			}
		} catch (Exception err) {
			log.error("[setCanDraw]", err);
		}
		return false;
	}

	public boolean setCanShare(String sid, String publicSID, boolean canShare) {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);

			Sessiondata sd = sessionDao.check(sid);
			if (AuthLevelUtil.hasUserLevel(userDao.getRights(sd.getUserId()))) {
				if (currentClient.getIsMod()) {
					Client rcl = sessionManager.getClientByPublicSID(publicSID, null);

					if (rcl != null) {
						rcl.setCanShare(canShare);
						sessionManager.updateClientByStreamId(rcl.getStreamid(), rcl, false, null);

						Map<Integer, Object> newMessage = new HashMap<>();
						newMessage.put(0, "updateDrawStatus");
						newMessage.put(1, rcl);
						scopeAdapter.sendMessageWithClientWithSyncObject(newMessage, true);
						return true;
					}
				}
			}
		} catch (Exception err) {
			log.error("[setCanShare]", err);
		}
		return false;
	}

	public boolean setCanRemote(String sid, String publicSID, boolean canRemote) {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);

			Sessiondata sd = sessionDao.check(sid);
			if (AuthLevelUtil.hasUserLevel(userDao.getRights(sd.getUserId()))) {
				if (currentClient.getIsMod()) {
					Client rcl = sessionManager.getClientByPublicSID(publicSID, null);

					if (rcl != null) {
						rcl.setCanRemote(canRemote);
						sessionManager.updateClientByStreamId(rcl.getStreamid(), rcl, false, null);

						Map<Integer, Object> newMessage = new HashMap<>();
						newMessage.put(0, "updateDrawStatus");
						newMessage.put(1, rcl);
						scopeAdapter.sendMessageWithClientWithSyncObject(newMessage, true);
						return true;
					}
				}
			}
		} catch (Exception err) {
			log.error("[setCanDraw]", err);
		}
		return false;
	}

	public boolean setCanGiveAudio(String sid, String publicSID, boolean canGiveAudio) {
		try {
			log.debug("[setCanGiveAudio] " + sid + ", " + publicSID + ", " + canGiveAudio);
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);

			Sessiondata sd = sessionDao.check(sid);
			if (AuthLevelUtil.hasUserLevel(userDao.getRights(sd.getUserId()))) {
				if (currentClient.getIsMod()) {
					Client rcl = sessionManager.getClientByPublicSID(publicSID, null);

					if (rcl != null) {
						rcl.setCanGiveAudio(canGiveAudio);
						sessionManager.updateClientByStreamId(rcl.getStreamid(), rcl, false, null);

						Map<Integer, Object> newMessage = new HashMap<>();
						newMessage.put(0, "updateGiveAudioStatus");
						newMessage.put(1, rcl);
						scopeAdapter.sendMessageWithClientWithSyncObject(newMessage, true);
						return true;
					}
				}
			}
		} catch (Exception err) {
			log.error("[setCanGiveAudio]", err);
		}
		return false;
	}

	public WhiteboardSyncLockObject startNewSyncprocess() {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			WhiteboardSyncLockObject wSyncLockObject = new WhiteboardSyncLockObject();
			wSyncLockObject.setAddtime(new Date());
			wSyncLockObject.setPublicSID(currentClient.getPublicSID());
			wSyncLockObject.setInitialLoaded(true);

			Map<String, WhiteboardSyncLockObject> syncListRoom = wbListManager.getWhiteBoardSyncListByRoomid(roomId);

			wSyncLockObject.setCurrentLoadingItem(true);
			wSyncLockObject.setInserted(new Date());

			syncListRoom.put(currentClient.getPublicSID(), wSyncLockObject);
			wbListManager.setWhiteBoardSyncListByRoomid(roomId, syncListRoom);

			//Sync to clients
			scopeAdapter.sendMessageToCurrentScope("sendSyncFlag", wSyncLockObject, true);

			return wSyncLockObject;
		} catch (Exception err) {
			log.error("[startNewSyncprocess]", err);
		}
		return null;
	}

	public void sendCompletedSyncEvent() {
		try {
			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			Map<String, WhiteboardSyncLockObject> syncListRoom = wbListManager.getWhiteBoardSyncListByRoomid(roomId);

			WhiteboardSyncLockObject wSyncLockObject = syncListRoom.get(currentClient.getPublicSID());

			if (wSyncLockObject == null) {
				log.error("WhiteboardSyncLockObject not found for this Client "
						+ syncListRoom);
				return;
			} else if (!wSyncLockObject.isCurrentLoadingItem()) {
				log.warn("WhiteboardSyncLockObject was not started yet " + syncListRoom);
				return;
			} else {
				syncListRoom.remove(currentClient.getPublicSID());
				wbListManager.setWhiteBoardSyncListByRoomid(roomId, syncListRoom);

				int numberOfInitial = getNumberOfInitialLoaders(syncListRoom);

				if (numberOfInitial == 0) {
					scopeAdapter.sendMessageToCurrentScope("sendSyncCompleteFlag", wSyncLockObject, true);
				} else {
					return;
				}
			}
		} catch (Exception err) {
			log.error("[sendCompletedSyncEvent]", err);
		}
		return;
	}

	private static int getNumberOfInitialLoaders(Map<String, WhiteboardSyncLockObject> syncListRoom) {
		int number = 0;
		for (Map.Entry<String, WhiteboardSyncLockObject> e : syncListRoom.entrySet()) {
			if (e.getValue().isInitialLoaded()) {
				number++;
			}
		}
		return number;
	}

	/*
	 * Image Sync Sequence
	 */

	public void startNewObjectSyncProcess(String objectId, boolean isStarting) {
		try {
			log.debug("startNewObjectSyncprocess: " + objectId);

			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			WhiteboardSyncLockObject wSyncLockObject = new WhiteboardSyncLockObject();
			wSyncLockObject.setAddtime(new Date());
			wSyncLockObject.setPublicSID(currentClient.getPublicSID());
			wSyncLockObject.setInserted(new Date());

			Map<String, WhiteboardSyncLockObject> syncListImage = wbListManager.getWhiteBoardObjectSyncListByRoomAndObjectId(roomId, objectId);
			syncListImage.put(currentClient.getPublicSID(), wSyncLockObject);
			wbListManager.setWhiteBoardImagesSyncListByRoomAndObjectId(roomId, objectId, syncListImage);

			// Do only send the Token to show the Loading Splash for the
			// initial-Request that starts the loading
			if (isStarting) {
				scopeAdapter.sendMessageToCurrentScope("sendObjectSyncFlag", wSyncLockObject, true);
			}
		} catch (Exception err) {
			log.error("[startNewObjectSyncProcess]", err);
		}
	}

	public int sendCompletedObjectSyncEvent(String objectId) {
		try {
			log.debug("sendCompletedObjectSyncEvent: " + objectId);

			IConnection current = Red5.getConnectionLocal();
			String streamid = current.getClient().getId();
			Client currentClient = sessionManager.getClientByStreamId(streamid, null);
			Long roomId = currentClient.getRoomId();

			Map<String, WhiteboardSyncLockObject> syncListImage = wbListManager.getWhiteBoardObjectSyncListByRoomAndObjectId(roomId, objectId);

			log.debug("sendCompletedObjectSyncEvent syncListImage: " + syncListImage);

			WhiteboardSyncLockObject wSyncLockObject = syncListImage.get(currentClient.getPublicSID());

			if (wSyncLockObject == null) {
				log.error("WhiteboardSyncLockObject not found for this Client " + currentClient.getPublicSID());
				log.error("WhiteboardSyncLockObject not found for this syncListImage " + syncListImage);
				return -2;
			} else {
				log.debug("sendCompletedImagesSyncEvent remove: " + currentClient.getPublicSID());

				syncListImage.remove(currentClient.getPublicSID());
				wbListManager.setWhiteBoardImagesSyncListByRoomAndObjectId(roomId, objectId, syncListImage);

				int numberOfInitial = wbListManager.getWhiteBoardObjectSyncListByRoomid(roomId).size();

				log.debug("sendCompletedImagesSyncEvent numberOfInitial: " + numberOfInitial);

				if (numberOfInitial == 0) {
					scopeAdapter.sendMessageToCurrentScope("sendObjectSyncCompleteFlag", wSyncLockObject, true);
					return 1;
				} else {
					return -4;
				}
			}
		} catch (Exception err) {
			log.error("[sendCompletedObjectSyncEvent]", err);
		}
		return -1;
	}

	public synchronized void removeUserFromAllLists(IScope scope, Client currentClient) {
		try {
			Long roomId = currentClient.getRoomId();

			// TODO: Maybe we should also check all rooms, independent from the
			// current roomId if there is any user registered
			if (roomId != null) {
				log.debug("removeUserFromAllLists this.whiteBoardObjectListManager: " + wbListManager);
				log.debug("removeUserFromAllLists roomId: " + roomId);

				// Check Initial Loaders
				Map<String, WhiteboardSyncLockObject> syncListRoom = wbListManager.getWhiteBoardSyncListByRoomid(roomId);

				WhiteboardSyncLockObject wSyncLockObject = syncListRoom.get(currentClient.getPublicSID());

				if (wSyncLockObject != null) {
					syncListRoom.remove(currentClient.getPublicSID());
				}
				wbListManager.setWhiteBoardSyncListByRoomid(roomId, syncListRoom);

				int numberOfInitial = getNumberOfInitialLoaders(syncListRoom);

				log.debug("scope " + scope);

				if (numberOfInitial == 0 && scope != null) {
					scopeAdapter.sendMessageToCurrentScope("" + roomId, "sendSyncCompleteFlag", wSyncLockObject, false);
				}

				// Check Image Loaders
				Map<String, Map<String, WhiteboardSyncLockObject>> syncListRoomImages = wbListManager.getWhiteBoardObjectSyncListByRoomid(roomId);

				for (Map.Entry<String, Map<String, WhiteboardSyncLockObject>> e : syncListRoomImages.entrySet()) {
					if (e.getValue().containsKey(currentClient.getPublicSID())) {
						e.getValue().remove(currentClient.getPublicSID());
					}
					wbListManager.setWhiteBoardImagesSyncListByRoomAndObjectId(roomId, e.getKey(), e.getValue());
				}

				int numberOfImageLoaders = wbListManager.getWhiteBoardObjectSyncListByRoomid(roomId).size();

				if (numberOfImageLoaders == 0 && scope != null) {
					scopeAdapter.sendMessageToCurrentScope("" + roomId, "sendImagesSyncCompleteFlag", new Object[] { "remove" }, true);
				}
			}
		} catch (Exception err) {
			log.error("[removeUserFromAllLists]", err);
		}
	}

	public Cliparts getClipArtIcons() {
		try {
			File clipart_dir = OmFileHelper.getPublicClipartsDir();

			FilenameFilter getFilesOnly = new FilenameFilter() {
				@Override
				public boolean accept(File b, String name) {
					File f = new File(b, name);
					return !f.isDirectory();
				}
			};

			FilenameFilter getDirectoriesOnly = new FilenameFilter() {
				@Override
				public boolean accept(File b, String name) {
					File f = new File(b, name);
					return f.isDirectory() && !f.getName().equals("thumb");
				}
			};

			Cliparts cl = new Cliparts();
			cl.setFolderName("general");

			Comparator<String> comparator = ComparatorUtils.naturalComparator();
			String[] files_general = clipart_dir.list(getFilesOnly);
			if (files_general != null) {
				Arrays.sort(files_general, comparator);
				cl.setGeneralList(files_general);
			}
			cl.setSubCategories(new LinkedList<Cliparts>());

			File[] dirs = clipart_dir.listFiles(getDirectoriesOnly);
			if (dirs != null) {
				for (File dir : dirs) {
					Cliparts cl_sub = new Cliparts();
					cl_sub.setFolderName("math");
					String[] files = dir.list(getFilesOnly);
					if (files != null) {
						Arrays.sort(files, comparator);
						cl_sub.setGeneralList(files);
						cl.getSubCategories().add(cl_sub);
					}
				}
			}

			return cl;
		} catch (Exception err) {
			log.error("[getClipArtIcons]", err);
		}
		return null;
	}

	@Override
	public void resultReceived(IPendingServiceCall arg0) {
		log.debug("resultReceived: " + arg0);
	}
}