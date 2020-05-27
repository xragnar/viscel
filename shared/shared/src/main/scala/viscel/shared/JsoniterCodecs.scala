package viscel.shared



object JsoniterCodecs {

  import com.github.plokhotnyuk.jsoniter_scala.macros._
  import com.github.plokhotnyuk.jsoniter_scala.core._


  implicit val StringRw: JsonValueCodec[String] = JsonCodecMaker.make


  implicit def vidRW: JsonValueCodec[Vid] = new JsonValueCodec[Vid] {
    override def decodeValue(in: JsonReader, default: Vid): Vid = Vid.from(in.readString(""))
    override def encodeValue(x: Vid, out: JsonWriter): Unit = out.writeVal(x.str)
    override def nullValue: Vid = null.asInstanceOf[Vid]
  }
  implicit val DescriptionRW: JsonValueCodec[Description] = JsonCodecMaker.make
  implicit val SharedImageRW: JsonValueCodec[SharedImage] = JsonCodecMaker.make
  implicit val BlobRW       : JsonValueCodec[Blob]        = JsonCodecMaker.make
  implicit val ChapterPosRW : JsonValueCodec[ChapterPos]  = JsonCodecMaker.make
  implicit val ContentsRW   : JsonValueCodec[Contents]    = JsonCodecMaker.make
  implicit val BookmarkRW   : JsonValueCodec[Bookmark]    = JsonCodecMaker.make


  implicit val VurlRw: JsonValueCodec[Vurl] = new JsonValueCodec[Vurl] {
    override def decodeValue(in: JsonReader, default: Vurl): Vurl = Vurl.unsafeFromString(in.readString(""))
    override def encodeValue(x: Vurl, out: JsonWriter): Unit = out.writeVal(x.uriString())
    override def nullValue: Vurl = null.asInstanceOf[Vurl]
  }

  implicit val DataRowRw: JsonValueCodec[DataRow]       = JsonCodecMaker.make(CodecMakerConfig.withDiscriminatorFieldName(None))
  implicit val DataRowListRw: JsonValueCodec[List[DataRow]] = JsonCodecMaker.make


}