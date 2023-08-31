/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.outsystems.plugins.camera.controller.*
import com.outsystems.plugins.camera.controller.helper.OSCAMRExifHelper
import com.outsystems.plugins.camera.controller.helper.OSCAMRFileHelper
import com.outsystems.plugins.camera.controller.helper.OSCAMRImageHelper
import com.outsystems.plugins.camera.controller.helper.OSCAMRMediaHelper
import com.outsystems.plugins.camera.model.OSCAMREditParameters
import com.outsystems.plugins.camera.model.OSCAMRMediaType
import com.outsystems.plugins.camera.model.OSCAMRError
import com.outsystems.plugins.camera.model.OSCAMRMediaResult
import com.outsystems.plugins.camera.model.OSCAMRParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.*

/**
 * This class launches the camera view, allows the user to take a picture, closes the camera view,
 * and returns the captured image.  When the camera view is closed, the screen displayed before
 * the camera view was shown is redisplayed.
 */
class CameraLauncher : CordovaPlugin() {
    private var mQuality // Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
            = 0
    private var targetWidth // desired width of the image
            = 0
    private var targetHeight // desired height of the image
            = 0
    private var imageUri // Uri of captured image
            : Uri? = null
    private var imageFilePath // File where the image is stored
            : String? = null
    private var encodingType // Type of encoding to use
            = 0
    private var mediaType // What type of media to retrieve
            = 0
    private var destType // Source type (needs to be saved for the permission handling)
            = 0
    private var srcType // Destination type (needs to be saved for permission handling)
            = 0
    private var saveToPhotoAlbum // Should the picture be saved to the device's photo album
            = false
    private var correctOrientation // Should the pictures orientation be corrected
            = false
    private var orientationCorrected // Has the picture's orientation been corrected
            = false
    private var allowEdit // Should we allow the user to crop the image.
            = false
    private var saveVideoToGallery
            = false // Should we allow the user to save the video in the gallery
    private var includeMetadata
            = false // Should we allow the app to obtain metadata about the media item
    private var latestVersion
            = false // Used to distinguish between the deprecated and latest version
    private var editParameters = OSCAMREditParameters(
        "",
        fromUri = false,
        saveToGallery = false,
        includeMetadata = false
    )
    var callbackContext: CallbackContext? = null
    private var numPics = 0
    private var conn // Used to update gallery app with newly-written files
            : MediaScannerConnection? = null
    private var scanMe // Uri of image to be added to content store
            : Uri? = null
    private var croppedUri: Uri? = null
    private var croppedFilePath: String? = null

    private lateinit var applicationId: String
    private var pendingDeleteMediaUri: Uri? = null
    private var camController: OSCAMRController? = null
    private var camParameters: OSCAMRParameters? = null

    private var galleryMediaType: OSCAMRMediaType = OSCAMRMediaType.IMAGE_AND_VIDEO
    private var allowMultipleSelection: Boolean = false

    override fun pluginInitialize() {
        super.pluginInitialize()

        //Adding an API to CoreAndroid to get the BuildConfigValue
        //This allows us to not make this a breaking change to embedding
        applicationId =
            BuildHelper.getBuildConfigValue(cordova.activity, "APPLICATION_ID") as String
        applicationId = preferences.getString("applicationId", applicationId)
        camController = OSCAMRController(
            applicationId,
            OSCAMRExifHelper(),
            OSCAMRFileHelper(),
            OSCAMRMediaHelper(),
            OSCAMRImageHelper()
        )

        camController?.deleteVideoFilesFromCache(cordova.activity)

    }

    override fun onDestroy() {
        super.onDestroy()
        camController?.deleteVideoFilesFromCache(cordova.activity)
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  A PluginResult object with a status and message.
     */
    @Throws(JSONException::class)
    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        this.callbackContext = callbackContext

        /**
         * Fix for the OutSystems NativeShell
         * The com.outsystems.myapp.BuildConfig class from BuildHelper.getBuildConfigValue is only created when using the cordova to build our app,
         * since we do not use cordova to build our app, we must add this condition to ensure that the applicationId is not null.
         * TODO: Remove this condition when we start to use cordova build command to build our applications.
         */
        if (applicationId == null) applicationId = cordova.activity.packageName

        when(action) {
            "takePicture" -> {
                srcType = CAMERA
                destType = FILE_URI
                saveToPhotoAlbum = false
                targetHeight = 0
                targetWidth = 0
                encodingType = JPEG
                mediaType = PICTURE
                mQuality = 50

                val parameters = args.getJSONObject(0)
                //Take the values from the arguments if they're not already defined (this is tricky)
                mQuality = parameters.getInt(QUALITY)
                targetWidth = parameters.getInt(WIDTH)
                targetHeight = parameters.getInt(HEIGHT)
                encodingType = parameters.getInt(ENCODING_TYPE)
                allowEdit = parameters.getBoolean(ALLOW_EDIT)
                correctOrientation = parameters.getBoolean(CORRECT_ORIENTATION)
                saveToPhotoAlbum = parameters.getBoolean(SAVE_TO_ALBUM)
                destType = parameters.getInt(DEST_TYPE)
                srcType = parameters.getInt(SOURCE_TYPE)
                mediaType = parameters.getInt(MEDIA_TYPE)
                includeMetadata = false
                latestVersion = false

                if (parameters.has(INCLUDE_METADATA)) {
                    includeMetadata = parameters.getBoolean(INCLUDE_METADATA)
                }

                if (parameters.has(LATEST_VERSION)) {
                    latestVersion = parameters.getBoolean(LATEST_VERSION)
                }

                // If the user specifies a 0 or smaller width/height
                // make it -1 so later comparisons succeed
                if (targetWidth < 1) {
                    targetWidth = -1
                }
                if (targetHeight < 1) {
                    targetHeight = -1
                }

                // We don't return full-quality PNG files. The camera outputs a JPEG
                // so requesting it as a PNG provides no actual benefit
                if (targetHeight == -1 && targetWidth == -1 && mQuality == 100 &&
                    !correctOrientation && encodingType == PNG && srcType == CAMERA
                ) {
                    encodingType = JPEG
                }

                //create CameraParameters
                camParameters = OSCAMRParameters(
                    mQuality,
                    targetWidth,
                    targetHeight,
                    encodingType,
                    mediaType,
                    allowEdit,
                    correctOrientation,
                    saveToPhotoAlbum,
                    includeMetadata,
                    latestVersion
                )

                try {
                    if (srcType == CAMERA) {
                        callTakePicture(destType, encodingType)
                    } else if (srcType == PHOTOLIBRARY || srcType == SAVEDPHOTOALBUM) {
                        callGetImage(srcType, destType, encodingType)
                    }
                } catch (e: IllegalArgumentException) {
                    callbackContext.error("Illegal Argument Exception")
                    val r = PluginResult(PluginResult.Status.ERROR)
                    callbackContext.sendPluginResult(r)
                    return true
                }
                val r = PluginResult(PluginResult.Status.NO_RESULT)
                r.keepCallback = true
                callbackContext.sendPluginResult(r)

            }
            "editPicture" -> callEditImage(args)
            "editURIPicture" -> {
                editParameters = OSCAMREditParameters(
                    args.getJSONObject(0).getString(URI),
                    true,
                    args.getJSONObject(0).getBoolean(SAVE_TO_GALLERY),
                    args.getJSONObject(0).getBoolean(INCLUDE_METADATA)
                )
                callEditUriImage(editParameters)
            }
            "recordVideo" -> {
                saveVideoToGallery = args.getJSONObject(0).getBoolean(SAVE_TO_GALLERY)
                includeMetadata = args.getJSONObject(0).getBoolean(INCLUDE_METADATA)
                callCaptureVideo(saveVideoToGallery)
            }
            "chooseFromGallery" -> callChooseFromGalleryWithPermissions(args)
            "playVideo" -> callPlayVideo(args)
            else -> return false
        }

        return true
    }// Create the cache directory if it doesn't exist

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------
    private val tempDirectoryPath: String
        get() {
            val cache = cordova.activity.cacheDir
            // Create the cache directory if it doesn't exist
            cache.mkdirs()
            return cache.absolutePath
        }

    /**
     * Take a picture with the camera.
     * When an image is captured or the camera view is cancelled, the result is returned
     * in CordovaActivity.onActivityResult, which forwards the result to this.onActivityResult.
     *
     * The image can either be returned as a base64 string or a URI that points to the file.
     * To display base64 string in an img tag, set the source to:
     * img.src="data:image/jpeg;base64,"+result;
     * or to display URI in an img tag
     * img.src=result;
     *
     * @param returnType        Set the type of image to return.
     * @param encodingType           Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
     */
    fun callTakePicture(returnType: Int, encodingType: Int) {
        val saveAlbumPermission = Build.VERSION.SDK_INT < 33 &&
                PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                Build.VERSION.SDK_INT >= 33 &&
                PermissionHelper.hasPermission(this, READ_MEDIA_VIDEO) &&
                PermissionHelper.hasPermission(this, READ_MEDIA_IMAGES)
        var takePicturePermission = PermissionHelper.hasPermission(this, Manifest.permission.CAMERA)

        // CB-10120: The CAMERA permission does not need to be requested unless it is declared
        // in AndroidManifest.xml. This plugin does not declare it, but others may and so we must
        // check the package info to determine if the permission is present.
        if (!takePicturePermission) {
            takePicturePermission = true
            try {
                val packageManager = cordova.activity.packageManager
                val permissionsInPackage = packageManager.getPackageInfo(
                    cordova.activity.packageName,
                    PackageManager.GET_PERMISSIONS
                ).requestedPermissions
                if (permissionsInPackage != null) {
                    for (permission in permissionsInPackage) {
                        if (permission == Manifest.permission.CAMERA) {
                            takePicturePermission = false
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(LOG_TAG, e.message.toString())
            }
        }
        if (takePicturePermission && saveAlbumPermission) {
            cordova.setActivityResultCallback(this)
            camController?.takePicture(cordova.activity, returnType, encodingType)
        } else if (saveAlbumPermission && !takePicturePermission) {
            PermissionHelper.requestPermission(this, TAKE_PIC_SEC, Manifest.permission.CAMERA)
        } else if (!saveAlbumPermission && takePicturePermission && Build.VERSION.SDK_INT < 33) {
            PermissionHelper.requestPermissions(
                this,
                TAKE_PIC_SEC,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else if (!saveAlbumPermission && takePicturePermission && Build.VERSION.SDK_INT >= 33) {
            PermissionHelper.requestPermissions(
                this,
                TAKE_PIC_SEC,
                arrayOf(READ_MEDIA_VIDEO, READ_MEDIA_IMAGES)
            )
        } else {
            PermissionHelper.requestPermissions(this, TAKE_PIC_SEC, permissions)
        }
    }

    /**
     * Get image from photo library.
     *
     * @param srcType           The album to get image from.
     * @param returnType        Set the type of image to return.
     * @param encodingType
     */
    fun callGetImage(srcType: Int, returnType: Int, encodingType: Int) {

        if (Build.VERSION.SDK_INT < 33 && !PermissionHelper.hasPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            PermissionHelper.requestPermission(
                this,
                SAVE_TO_ALBUM_SEC,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else if (Build.VERSION.SDK_INT >= 33 && (!PermissionHelper.hasPermission(
                this,
                READ_MEDIA_IMAGES
            ) || !PermissionHelper.hasPermission(this, READ_MEDIA_VIDEO))
        ) {
            PermissionHelper.requestPermissions(
                this, SAVE_TO_ALBUM_SEC, arrayOf(
                    READ_MEDIA_VIDEO, READ_MEDIA_IMAGES
                )
            )
        } else {
            camParameters?.let {
                cordova.setActivityResultCallback(this)
                camController?.getImage(this.cordova.activity, srcType, returnType, it)
            }
        }
    }

    fun callEditImage(args: JSONArray) {
        editParameters = OSCAMREditParameters(
            "",
            fromUri = false,
            saveToGallery = false,
            includeMetadata = false
        )
        val imageBase64 = args.getString(0)
        cordova.setActivityResultCallback(this)
        camController?.editImage(cordova.activity, imageBase64, null, null)
    }

    fun callEditUriImage(editParameters: OSCAMREditParameters) {

        val galleryPermissionNeeded = !((Build.VERSION.SDK_INT < 33 &&
                PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) ||
                (Build.VERSION.SDK_INT >= 33 &&
                        PermissionHelper.hasPermission(this, READ_MEDIA_VIDEO) &&
                        PermissionHelper.hasPermission(this, READ_MEDIA_IMAGES)))

        if (galleryPermissionNeeded) {
            if (Build.VERSION.SDK_INT < 33) {
                var permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (editParameters.saveToGallery) {
                    permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
                PermissionHelper.requestPermissions(
                    this,
                    EDIT_PICTURE_SEC,
                    permissions
                )
            }
            else {
                PermissionHelper.requestPermissions(
                    this,
                    EDIT_PICTURE_SEC,
                    arrayOf(READ_MEDIA_IMAGES)
                )
            }
            return
        }

        if (editParameters.editURI.isNullOrEmpty()) {
            sendError(OSCAMRError.EDIT_PICTURE_EMPTY_URI_ERROR)
            return
        }
        cordova.setActivityResultCallback(this)
        camController?.editURIPicture(cordova.activity, editParameters.editURI!!, null, null
        ) {
            sendError(it)
        }
    }

    fun callCaptureVideo(saveVideoToGallery: Boolean) {

        val cameraPermissionNeeded = !PermissionHelper.hasPermission(this, Manifest.permission.CAMERA)

        val galleryPermissionNeeded = saveVideoToGallery && !((Build.VERSION.SDK_INT < 33 &&
                PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) ||
                (Build.VERSION.SDK_INT >= 33 &&
                        PermissionHelper.hasPermission(this, READ_MEDIA_VIDEO) &&
                        PermissionHelper.hasPermission(this, READ_MEDIA_IMAGES)))

        if (cameraPermissionNeeded && galleryPermissionNeeded) {
            PermissionHelper.requestPermissions(this, CAPTURE_VIDEO_SEC, permissions)
            return
        }

        else if (cameraPermissionNeeded) {
            PermissionHelper.requestPermission(
                this,
                CAPTURE_VIDEO_SEC,
                Manifest.permission.CAMERA
            )
            return
        }
        else if (galleryPermissionNeeded) {
            if (Build.VERSION.SDK_INT < 33) {
                PermissionHelper.requestPermissions(
                    this,
                    CAPTURE_VIDEO_SEC,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
            else {
                PermissionHelper.requestPermissions(
                    this,
                    CAPTURE_VIDEO_SEC,
                    arrayOf(READ_MEDIA_VIDEO, READ_MEDIA_IMAGES)
                )
            }
            return
        }

        cordova.setActivityResultCallback(this)
        camController?.captureVideo(cordova.activity, saveVideoToGallery) {
            sendError(it)
        }
    }

    /**
     * Calls the "Choose from gallery" method and the relevant permissions to access the gallery.
     * @param args A Json array containing the parameters for "Choose from gallery".
     */
    fun callChooseFromGalleryWithPermissions(args: JSONArray) {

        try {
            val parameters = args.getJSONObject(0)
            galleryMediaType = OSCAMRMediaType.fromValue(parameters.getInt(MEDIA_TYPE))
            allowMultipleSelection = parameters.getBoolean(ALLOW_MULTIPLE)
            includeMetadata = parameters.getBoolean(INCLUDE_METADATA)
            allowEdit = parameters.getBoolean(ALLOW_EDIT)
        }
        catch(_: Exception) {
            sendError(OSCAMRError.GENERIC_CHOOSE_MULTIMEDIA_ERROR)
            return
        }

        if (Build.VERSION.SDK_INT < 33
            && !PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {

            PermissionHelper.requestPermission(
                this,
                OSCAMRController.CHOOSE_FROM_GALLERY_PERMISSION_CODE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        else if (Build.VERSION.SDK_INT >= 33
            && (!PermissionHelper.hasPermission(this, READ_MEDIA_IMAGES)
                    || !PermissionHelper.hasPermission(this, READ_MEDIA_VIDEO))) {
            PermissionHelper.requestPermissions(
                this,
                OSCAMRController.CHOOSE_FROM_GALLERY_PERMISSION_CODE,
                arrayOf(READ_MEDIA_VIDEO, READ_MEDIA_IMAGES)
            )
        }
        else {
            callChooseFromGallery()
        }
    }

    /**
     * Calls the "Choose from gallery" method.
     */
    private fun callChooseFromGallery() {
        cordova.setActivityResultCallback(this)
        camController?.chooseFromGallery(
            this.cordova.activity,
            galleryMediaType,
            allowMultipleSelection,
            OSCAMRController.CHOOSE_FROM_GALLERY_REQUEST_CODE
        )
    }

    /**
     * Calls the "Play Video" method.
     * @param args A Json array containing the parameters for the feature.
     */
    private fun callPlayVideo(args: JSONArray) {
        try {
            val videoUri = args.getJSONObject(0).getString(VIDEO_URI)
            camController?.playVideo(cordova.activity, videoUri,
                {
                    sendSuccessfulResult("")
                },{
                    sendError(it)
                }
            )
        }
        catch(_: Exception) {
            sendError(OSCAMRError.PLAY_VIDEO_GENERAL_ERROR)
            return
        }
    }

    /**
     * Called when the camera view exits.
     *
     * @param requestCode The request code originally supplied to startActivityForResult(),
     * allowing you to identify who this result came from.
     * @param resultCode  The integer result code returned by the child activity through its setResult().
     * @param intent      An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {

        if(requestCode == OSCAMRController.CHOOSE_FROM_GALLERY_REQUEST_CODE) {
            if(camController == null) {
                sendError(OSCAMRError.GENERIC_CHOOSE_MULTIMEDIA_ERROR)
                return
            }

            if (allowEdit && galleryMediaType == OSCAMRMediaType.IMAGE) {
                /* This ensures the plugin is called when the result from image edition is returned;
                When the plugin migrates to the new structure, this call should be
                made from the library and implemented by cordova plugin, just like in H&F plugin.
                 */
                cordova.setActivityResultCallback(this)
            }

            CoroutineScope(Dispatchers.Default).launch {
                camController!!.onChooseFromGalleryResult(
                    cordova.activity,
                    resultCode,
                    intent,
                    includeMetadata,
                    allowEdit,
                    galleryMediaType,
                    { sendSuccessfulResult(it) },
                    { sendError(it) })
            }
            return
        }

        if (requestCode == OSCAMRController.EDIT_FROM_GALLERY_REQUEST_CODE) {
            CoroutineScope(Dispatchers.Default).launch {
                camController!!.onChooseFromGalleryEditResult(
                    cordova.activity,
                    resultCode,
                    intent,
                    includeMetadata,
                    { sendSuccessfulResult(it) },
                    { sendError(it) })
            }
            return
        }

        // Get src and dest types from request code for a Camera Activity
        val srcType = requestCode / 16 - 1
        var destType = requestCode % 16 - 1
        if (requestCode == CROP_GALERY) {
            if (resultCode == Activity.RESULT_OK) {
                editParameters.fromUri = false
                camController?.processResultFromEdit(cordova.activity, intent, editParameters,
                    {
                        callbackContext?.success(it)
                    },
                    {
                        // do nothing, because this callback shouldn't be called in this case
                    },
                    {
                        sendError(OSCAMRError.EDIT_IMAGE_ERROR)
                    })
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(OSCAMRError.NO_IMAGE_SELECTED_ERROR)
            } else {
                sendError(OSCAMRError.EDIT_IMAGE_ERROR)
            }
        } else if (requestCode >= CROP_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {

                // Because of the inability to pass through multiple intents, this hack will allow us
                // to pass arcane codes back.
                destType = requestCode - CROP_CAMERA
                try {
                    camParameters?.let { it ->
                        camController?.processResultFromCamera(
                            cordova.activity,
                            intent,
                            it,
                            { image ->
                                val pluginResult = PluginResult(PluginResult.Status.OK, image)
                                this.callbackContext?.sendPluginResult(pluginResult)
                            },
                            { mediaResult ->
                                val gson = GsonBuilder().create()
                                val resultJson = gson.toJson(mediaResult)
                                val pluginResult = PluginResult(PluginResult.Status.OK, resultJson)
                                callbackContext?.sendPluginResult(pluginResult)
                            },
                            { error ->
                                sendError(error)
                            }
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    LOG.e(LOG_TAG, "Unable to write to file")
                }
            } // If cancelled
            else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(OSCAMRError.NO_PICTURE_TAKEN_ERROR)
            } else {
                sendError(OSCAMRError.EDIT_IMAGE_ERROR)
            }
        } else if (srcType == CAMERA) {
            // If image available
            if (resultCode == Activity.RESULT_OK) {
                try {
                    if (allowEdit && camController != null) {
                        val tmpFile = FileProvider.getUriForFile(
                            cordova.activity,
                            "$applicationId.camera.provider",
                            camController!!.createCaptureFile(
                                cordova.activity,
                                encodingType,
                                cordova.activity.getSharedPreferences(
                                    STORE,
                                    Context.MODE_PRIVATE
                                ).getString(EDIT_FILE_NAME_KEY, "") ?: ""
                            )
                        )
                        cordova.setActivityResultCallback(this)
                        camController?.openCropActivity(
                            cordova.activity,
                            tmpFile,
                            CROP_CAMERA,
                            destType
                        )
                    } else {
                        val photoUri = intent?.extras?.get(CameraActivity.ARG_PHOTO_URI) as? Uri?
                        camParameters?.let { params ->
                            camController?.processResultFromCameraX(
                                cordova.activity,
                                params,
                                photoUri,
                                {
                                    val pluginResult = PluginResult(PluginResult.Status.OK, it)
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                },
                                { mediaResult ->
                                    val gson = GsonBuilder().create()
                                    val resultJson = gson.toJson(mediaResult)
                                    val pluginResult = PluginResult(PluginResult.Status.OK, resultJson)
                                    callbackContext?.sendPluginResult(pluginResult)
                                },
                                {
                                    val pluginResult = PluginResult(PluginResult.Status.ERROR, it.toString())
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                }
                            )
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    sendError(OSCAMRError.TAKE_PHOTO_ERROR)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(OSCAMRError.NO_PICTURE_TAKEN_ERROR)
            } else {
                sendError(OSCAMRError.TAKE_PHOTO_ERROR)
            }
        } else if (srcType == PHOTOLIBRARY || srcType == SAVEDPHOTOALBUM) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                if (allowEdit) {
                    cordova.setActivityResultCallback(this)
                    val uri = intent.data
                    camController?.openCropActivity(cordova.activity, uri, CROP_GALERY, destType)
                } else {
                    cordova.threadPool.execute {
                        camParameters?.let { params ->
                            camController?.processResultFromGallery(
                                this.cordova.activity,
                                intent,
                                params,
                                {
                                    val pluginResult = PluginResult(PluginResult.Status.OK, it)
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                },
                                {
                                    val pluginResult =
                                        PluginResult(PluginResult.Status.ERROR, it.toString())
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                })
                        }
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(OSCAMRError.NO_IMAGE_SELECTED_ERROR)
            } else {
                sendError(OSCAMRError.GET_IMAGE_ERROR)
            }
        } else if (requestCode == OSCAMRController.EDIT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                camController?.processResultFromEdit(cordova.activity, intent, editParameters,
                    {
                        val pluginResult = PluginResult(PluginResult.Status.OK, it)
                        this.callbackContext?.sendPluginResult(pluginResult)
                    },
                    { mediaResult ->
                        val gson = GsonBuilder().create()
                        val resultJson = gson.toJson(mediaResult)
                        val pluginResult = PluginResult(PluginResult.Status.OK, resultJson)
                        callbackContext?.sendPluginResult(pluginResult)
                    },
                    {
                        sendError(it)
                    }
                )
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(OSCAMRError.EDIT_CANCELLED_ERROR)
            } else {
                sendError(OSCAMRError.EDIT_IMAGE_ERROR)
            }
        } else if (requestCode == OSCAMRMediaHelper.REQUEST_VIDEO_CAPTURE || requestCode == OSCAMRMediaHelper.REQUEST_VIDEO_CAPTURE_SAVE_TO_GALLERY) {
            if (resultCode == Activity.RESULT_OK) {
                // Check if intent and data (Uri) are not null
                var uri = intent?.data
                if (uri == null) {
                    val fromPreferences = cordova.activity.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(STORE, "")
                    fromPreferences.let {  uri = Uri.parse(fromPreferences) }
                }
                if(cordova.activity == null) {
                    sendError(OSCAMRError.CAPTURE_VIDEO_ERROR)
                    return
                }

                CoroutineScope(Dispatchers.Default).launch {
                    camController?.processResultFromVideo(
                        cordova.activity,
                        uri,
                        requestCode != OSCAMRMediaHelper.REQUEST_VIDEO_CAPTURE,
                        includeMetadata,
                        { mediaResult ->
                            val gson = GsonBuilder().create()
                            val resultJson = gson.toJson(mediaResult)
                            val pluginResult = PluginResult(PluginResult.Status.OK, resultJson)
                            callbackContext?.sendPluginResult(pluginResult)
                        },
                        {
                            sendError(OSCAMRError.CAPTURE_VIDEO_ERROR)
                        }
                    )
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(OSCAMRError.CAPTURE_VIDEO_CANCELLED_ERROR)
            } else {
                sendError(OSCAMRError.CAPTURE_VIDEO_ERROR)
            }
        } else if (requestCode == RECOVERABLE_DELETE_REQUEST) {
            // retry media store deletion ...
            val contentResolver = cordova.activity.contentResolver
            try {
                pendingDeleteMediaUri?.let { contentResolver.delete(it, null, null) }
            } catch (e: Exception) {
                LOG.e(LOG_TAG, "Unable to delete media store file after permission was granted")
            }
            pendingDeleteMediaUri = null
        }
    }

    /**
     * Creates a cursor that can be used to determine how many images we have.
     *
     * @return a cursor
     */
    private fun queryImgDB(contentStore: Uri): Cursor? {
        return cordova.activity.contentResolver.query(
            contentStore, arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null
        )
    }

    override fun onRequestPermissionResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        for (i in grantResults.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED && permissions[i] == Manifest.permission.CAMERA) {
                sendError(OSCAMRError.CAMERA_PERMISSION_DENIED_ERROR)
                return
            } else if (grantResults[i] == PackageManager.PERMISSION_DENIED && ((Build.VERSION.SDK_INT < 33
                        && (permissions[i] == Manifest.permission.READ_EXTERNAL_STORAGE || permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        || (Build.VERSION.SDK_INT >= 33
                        && (permissions[i] == READ_MEDIA_IMAGES || permissions[i] == READ_MEDIA_VIDEO)))
            ) {
                sendError(OSCAMRError.GALLERY_PERMISSION_DENIED_ERROR)
                return
            }
        }
        when (requestCode) {
            TAKE_PIC_SEC -> {
                cordova.setActivityResultCallback(this)
                camController?.takePicture(this.cordova.activity, destType, encodingType)
            }
            SAVE_TO_ALBUM_SEC -> callGetImage(srcType, destType, encodingType)
            CAPTURE_VIDEO_SEC -> callCaptureVideo(saveVideoToGallery)
            OSCAMRController.CHOOSE_FROM_GALLERY_PERMISSION_CODE -> callChooseFromGallery()
            EDIT_PICTURE_SEC -> callEditUriImage(editParameters)
        }
    }

    /**
     * Taking or choosing a picture launches another Activity, so we need to implement the
     * save/restore APIs to handle the case where the CordovaActivity is killed by the OS
     * before we get the launched Activity's result.
     */
    override fun onSaveInstanceState(): Bundle {
        val state = Bundle()
        state.putInt("destType", destType)
        state.putInt("srcType", srcType)
        state.putInt("mQuality", mQuality)
        state.putInt("targetWidth", targetWidth)
        state.putInt("targetHeight", targetHeight)
        state.putInt("encodingType", encodingType)
        state.putInt("mediaType", mediaType)
        state.putInt("numPics", numPics)
        state.putBoolean("allowEdit", allowEdit)
        state.putBoolean("correctOrientation", correctOrientation)
        state.putBoolean("saveToPhotoAlbum", saveToPhotoAlbum)
        if (croppedUri != null) {
            state.putString(CROPPED_URI_KEY, croppedFilePath)
        }
        if (imageUri != null) {
            state.putString(IMAGE_URI_KEY, imageFilePath)
        }
        if (imageFilePath != null) {
            state.putString(IMAGE_FILE_PATH_KEY, imageFilePath)
        }
        return state
    }

    override fun onRestoreStateForActivityResult(state: Bundle, callbackContext: CallbackContext) {
        destType = state.getInt("destType")
        srcType = state.getInt("srcType")
        mQuality = state.getInt("mQuality")
        targetWidth = state.getInt("targetWidth")
        targetHeight = state.getInt("targetHeight")
        encodingType = state.getInt("encodingType")
        mediaType = state.getInt("mediaType")
        numPics = state.getInt("numPics")
        allowEdit = state.getBoolean("allowEdit")
        correctOrientation = state.getBoolean("correctOrientation")
        saveToPhotoAlbum = state.getBoolean("saveToPhotoAlbum")
        if (state.containsKey(CROPPED_URI_KEY)) {
            croppedUri = Uri.parse(state.getString(CROPPED_URI_KEY))
        }
        if (state.containsKey(IMAGE_URI_KEY)) {
            //I have no idea what type of URI is being passed in
            imageUri = Uri.parse(state.getString(IMAGE_URI_KEY))
        }
        if (state.containsKey(IMAGE_FILE_PATH_KEY)) {
            imageFilePath = state.getString(IMAGE_FILE_PATH_KEY)
        }
        this.callbackContext = callbackContext
    }

    /**
     * Sends a successful result to cordova.
     * @param result The result data to be sent to cordova.
     */
    private fun sendSuccessfulResult(result: Any) {
        val gson = GsonBuilder().create()
        val resultJson = gson.toJson(result)
        val pluginResult = PluginResult(PluginResult.Status.OK, resultJson)
        this.callbackContext?.sendPluginResult(pluginResult)
    }

    private fun sendError(error: OSCAMRError) {
        val jsonResult = JSONObject()
        try {
            jsonResult.put("code", formatErrorCode(error.code))
            jsonResult.put("message", error.description)
            callbackContext?.error(jsonResult)
        } catch (e: JSONException) {
            LOG.d(LOG_TAG, "Error: JSONException occurred while preparing to send an error.")
            callbackContext?.error("There was an error performing the operation.")
        }
    }

    private fun formatErrorCode(code: Int): String {
        val stringCode = Integer.toString(code)
        return ERROR_FORMAT_PREFIX + "0000$stringCode".substring(stringCode.length)
    }

    companion object {
        private const val DATA_URL = 0 // Return base64 encoded string
        private const val FILE_URI =
            1 // Return file uri (content://media/external/images/media/2 for Android)
        private const val NATIVE_URI = 2 // On Android, this is the same as FILE_URI
        private const val PHOTOLIBRARY =
            0 // Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
        private const val CAMERA = 1 // Take picture from camera
        private const val SAVEDPHOTOALBUM =
            2 // Choose image from picture library (same as PHOTOLIBRARY for Android)
        private const val RECOVERABLE_DELETE_REQUEST = 3 // Result of Recoverable Security Exception
        private const val REQUEST_VIDEO_CAPTURE = 1
        private const val PICTURE =
            0 // allow selection of still pictures only. DEFAULT. Will return format specified via DestinationType
        private const val VIDEO = 1 // allow selection of video only, ONLY RETURNS URL
        private const val ALLMEDIA = 2 // allow selection from all media types
        private const val JPEG = 0 // Take a picture of type JPEG
        private const val PNG = 1 // Take a picture of type PNG
        private const val JPEG_TYPE = "jpg"
        private const val PNG_TYPE = "png"
        private const val JPEG_EXTENSION = "." + JPEG_TYPE
        private const val PNG_EXTENSION = "." + PNG_TYPE
        private const val PNG_MIME_TYPE = "image/png"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        private const val GET_PICTURE = "Get Picture"
        private const val GET_VIDEO = "Get Video"
        private const val GET_All = "Get All"
        private const val CROPPED_URI_KEY = "croppedUri"
        private const val IMAGE_URI_KEY = "imageUri"
        private const val IMAGE_FILE_PATH_KEY = "imageFilePath"
        private const val TAKE_PICTURE_ACTION = "takePicture"
        private const val TAKE_PIC_SEC = 0
        private const val SAVE_TO_ALBUM_SEC = 1
        private const val CAPTURE_VIDEO_SEC = 2
        private const val EDIT_PICTURE_SEC = 3

        private const val LOG_TAG = "CameraLauncher"

        //Where did this come from?
        private const val CROP_CAMERA = 100
        private const val CROP_GALERY = 666
        private const val TIME_FORMAT = "yyyyMMdd_HHmmss"

        //we need literal values because we cannot simply do Manifest.permission.READ_MEDIA_IMAGES, because of the target sdk
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"

        //for errors
        private const val ERROR_FORMAT_PREFIX = "OS-PLUG-CAMR-"
        protected val permissions = createPermissionArray()

        private const val STORE = "CameraStore"
        private const val EDIT_FILE_NAME_KEY = "EditFileName"
        private const val VIDEO_URI = "videoURI"
        private const val SAVE_TO_GALLERY = "saveToGallery"
        private const val INCLUDE_METADATA = "includeMetadata"
        private const val LATEST_VERSION = "latestVersion"
        private const val ALLOW_MULTIPLE = "allowMultipleSelection"
        private const val MEDIA_TYPE = "mediaType"
        private const val URI = "uri"

        //take picture json
        private const val QUALITY = "quality"
        private const val WIDTH = "targetWidth"
        private const val HEIGHT = "targetHeight"
        private const val ENCODING_TYPE = "encodingType"
        private const val ALLOW_EDIT = "allowEdit"
        private const val CORRECT_ORIENTATION = "correctOrientation"
        private const val SAVE_TO_ALBUM = "saveToPhotoAlbum"
        private const val SOURCE_TYPE = "sourceType"
        private const val CAMERA_DIRECTION = "caneraDirection"
        private const val DEST_TYPE = "destinationType"

        private fun createPermissionArray(): Array<String> {
            return if (Build.VERSION.SDK_INT < 33) {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } else {
                arrayOf(
                    Manifest.permission.CAMERA,
                    READ_MEDIA_IMAGES,
                    READ_MEDIA_VIDEO
                )
            }
        }
    }
}